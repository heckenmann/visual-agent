package de.heckenmann.visualagent.agent.codexdirect

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveAuthorizationCodeTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveRefreshTokenTokenResponseClient
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.net.URI
import java.time.Clock
import java.time.Instant

/** OpenAI-specific dynamic registration on Spring Security's authorization-code and JWT APIs. */
@Service
internal class DirectOAuthProtocol(
    @Qualifier("codexDirectWebClient") private val webClient: WebClient,
) {
    /** Exchanges one validated callback and verifies identity before producing a persistable record. */
    fun exchange(
        attempt: DirectOAuthAttempt,
        parameters: Map<String, String>,
        clock: Clock,
    ): Mono<DirectOAuthCredentials> =
        Mono.defer {
            val clientId = attempt.consume(parameters, clock)
            val registration = registration(clientId, requireNotNull(attempt.authorization.redirectUri))
            val authorizationResponse =
                OAuth2AuthorizationResponse
                    .success(requireNotNull(parameters["code"]))
                    .state(requireNotNull(attempt.authorization.state))
                    .redirectUri(requireNotNull(attempt.authorization.redirectUri))
                    .build()
            val grant =
                OAuth2AuthorizationCodeGrantRequest(
                    registration,
                    OAuth2AuthorizationExchange(attempt.authorization, authorizationResponse),
                )
            val client =
                WebClientReactiveAuthorizationCodeTokenResponseClient().apply {
                    setWebClient(webClient)
                    setParametersCustomizer { it.set("resource", DirectOAuthAttempt.RESOURCE) }
                }
            client.getTokenResponse(grant).flatMap { tokens ->
                require("chatgpt.tokens.use.direct" in tokens.accessToken.scopes) { "ChatGPT plan usage was not authorized" }
                val idToken = requireNotNull(tokens.additionalParameters["id_token"] as? String) { "Missing identity token" }
                val refresh = requireNotNull(tokens.refreshToken?.tokenValue) { "Missing renewable session" }
                decoder(clientId, attempt.nonce).decode(idToken).map { identity ->
                    require(!identity.subject.isNullOrBlank()) { "Missing verified account identity" }
                    require(identity.expiresAt != null && identity.issuedAt != null) { "Missing identity token timestamps" }
                    require(
                        attempt.previousSubject == null || identity.subject == attempt.previousSubject,
                    ) { "The selected account identity changed" }
                    DirectOAuthCredentials(
                        attempt.profileId,
                        clientId,
                        requireNotNull(identity.subject),
                        DirectOAuthAttempt.ISSUER,
                        tokens.accessToken.tokenValue,
                        refresh,
                        idToken,
                        requireNotNull(tokens.accessToken.expiresAt).epochSecond,
                        tokens.accessToken.scopes,
                    )
                }
            }
        }

    /** Refreshes the selected registration only; a failure never falls back to API-key billing. */
    fun refresh(record: DirectOAuthCredentials): Mono<DirectOAuthCredentials> =
        Mono.defer {
            require(record.refreshToken.isNotBlank()) { "Sign in with ChatGPT again" }
            val client =
                WebClientReactiveRefreshTokenTokenResponseClient().apply {
                    setWebClient(webClient)
                    setParametersCustomizer { parameters ->
                        parameters.set("resource", DirectOAuthAttempt.RESOURCE)
                        parameters.remove("scope")
                    }
                }
            val access =
                OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    record.accessToken,
                    Instant.EPOCH,
                    Instant.ofEpochSecond(record.expiresAtEpochSeconds),
                    record.scopes,
                )
            client
                .getTokenResponse(
                    OAuth2RefreshTokenGrantRequest(
                        registration(record.clientId, "http://127.0.0.1/auth/callback"),
                        access,
                        OAuth2RefreshToken(record.refreshToken, null),
                        emptySet(),
                    ),
                ).flatMap { tokens ->
                    val scopes = tokens.accessToken.scopes.ifEmpty { record.scopes }
                    require("chatgpt.tokens.use.direct" in scopes) { "ChatGPT plan usage was not authorized" }
                    val idToken = tokens.additionalParameters["id_token"] as? String
                    val validated =
                        if (idToken == null) {
                            Mono.just(record.subject)
                        } else {
                            decoder(record.clientId, null)
                                .decode(idToken)
                                .map { requireNotNull(it.subject) }
                        }
                    validated.map { subject ->
                        require(subject == record.subject) { "The refreshed account identity changed" }
                        record.copy(
                            accessToken = tokens.accessToken.tokenValue,
                            refreshToken = tokens.refreshToken?.tokenValue ?: record.refreshToken,
                            idToken = idToken ?: record.idToken,
                            scopes = scopes,
                            expiresAtEpochSeconds = requireNotNull(tokens.accessToken.expiresAt).epochSecond,
                            generation = record.generation + 1,
                        )
                    }
                }
        }

    /** Revokes the renewable session using OpenAI's discovered, same-origin revocation endpoint. */
    fun revoke(record: DirectOAuthCredentials): Mono<Boolean> =
        Mono.defer {
            if (record.refreshToken.isBlank()) return@defer Mono.just(false)
            webClient
                .get()
                .uri("${DirectOAuthAttempt.ISSUER}/.well-known/openid-configuration")
                .retrieve()
                .bodyToMono(String::class.java)
                .flatMap { document ->
                    val endpoint =
                        Json
                            .parseToJsonElement(document)
                            .jsonObject
                            .getValue("revocation_endpoint")
                            .jsonPrimitive.content
                    val uri = URI(endpoint)
                    require(
                        uri.scheme == "https" &&
                            uri.host == "auth.openai.com" &&
                            (uri.port == -1 || uri.port == 443) &&
                            uri.userInfo == null &&
                            uri.fragment == null,
                    ) {
                        "The revocation endpoint is not an authorized OpenAI endpoint"
                    }
                    webClient
                        .post()
                        .uri(uri)
                        .body(
                            BodyInserters
                                .fromFormData("token", record.refreshToken)
                                .with("token_type_hint", "refresh_token")
                                .with("client_id", record.clientId),
                        ).retrieve()
                        .toBodilessEntity()
                        .map { true }
                }
        }

    private fun decoder(
        clientId: String,
        nonce: String?,
    ): NimbusReactiveJwtDecoder =
        NimbusReactiveJwtDecoder
            .withJwkSetUri("${DirectOAuthAttempt.ISSUER}/.well-known/jwks.json")
            .webClient(webClient)
            .build()
            .apply {
                setJwtValidator(
                    DelegatingOAuth2TokenValidator<Jwt>(
                        JwtValidators.createDefaultWithIssuer(DirectOAuthAttempt.ISSUER),
                        OAuth2TokenValidator<Jwt> { jwt ->
                            val authorizedParty = jwt.getClaimAsString("azp")
                            if (clientId in jwt.audience.orEmpty() &&
                                (nonce == null || jwt.getClaimAsString("nonce") == nonce) &&
                                jwt.expiresAt != null &&
                                (authorizedParty == null || authorizedParty == clientId) &&
                                (jwt.audience.orEmpty().size <= 1 || authorizedParty == clientId)
                            ) {
                                OAuth2TokenValidatorResult.success()
                            } else {
                                OAuth2TokenValidatorResult.failure(
                                    OAuth2Error("invalid_token", "Identity token claims did not match", null),
                                )
                            }
                        },
                    ),
                )
            }

    private fun registration(
        clientId: String,
        callback: String,
    ): ClientRegistration =
        ClientRegistration
            .withRegistrationId(clientId)
            .clientId(clientId)
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(callback)
            .scope(DirectOAuthAttempt.SCOPES)
            .authorizationUri("${DirectOAuthAttempt.ISSUER}/api/accounts/authorize")
            .tokenUri(DirectOAuthAttempt.TOKEN_ENDPOINT)
            .build()
}
