package de.heckenmann.visualagent.agent.codexdirect

import org.springframework.security.crypto.keygen.Base64StringKeyGenerator
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import java.net.URI
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

/** One-use PKCE transaction retained only until callback completion or expiry. */
internal class DirectOAuthAttempt(
    val profileId: String,
    val authorization: OAuth2AuthorizationRequest,
    val nonce: String,
    val expiresAt: Instant,
    val previousClientId: String?,
    val previousSubject: String?,
) {
    private val consumed = AtomicBoolean()

    /** Validates correlation before consuming a transaction, including consent-denied callbacks. */
    fun consume(
        parameters: Map<String, String>,
        clock: Clock,
    ): String {
        require(clock.instant().isBefore(expiresAt)) { "The sign-in attempt expired" }
        require(MessageDigest.isEqual(requireNotNull(authorization.state).toByteArray(), parameters["state"].orEmpty().toByteArray())) {
            "The sign-in callback does not match the pending attempt"
        }
        check(consumed.compareAndSet(false, true)) { "The sign-in attempt was already consumed" }
        require(parameters["error"] == null) { "ChatGPT sign-in was denied or failed" }
        require(!parameters["code"].isNullOrBlank()) { "The sign-in callback did not contain an authorization code" }
        val issued = parameters["client_id"] ?: previousClientId
        require(!issued.isNullOrBlank() && issued != INITIAL_CLIENT) { "The sign-in callback did not contain an issued client ID" }
        require(previousClientId == null || issued == previousClientId) { "The sign-in callback changed the selected registration" }
        return issued
    }

    override fun toString(): String = "DirectOAuthAttempt(redacted)"

    companion object {
        const val INITIAL_CLIENT = "dynamic_agent_client"
        const val ISSUER = "https://auth.openai.com"
        const val TOKEN_ENDPOINT = "$ISSUER/api/accounts/oauth/token"
        const val RESOURCE = "https://api.openai.com/v1"
        val SCOPES = setOf("openid", "profile", "email", "offline_access", "resource.invoke", "chatgpt.tokens.use.direct")

        /** Creates fresh state, nonce and framework-generated S256 PKCE for a loopback callback. */
        fun create(
            profileId: String,
            hostId: String,
            callback: URI,
            clock: Clock,
            previous: DirectOAuthCredentials? = null,
        ): DirectOAuthAttempt {
            require(callback.scheme == "http" && callback.host == "127.0.0.1" && callback.path == "/auth/callback") {
                "ChatGPT sign-in requires an HTTP callback on 127.0.0.1 at /auth/callback"
            }
            require(callback.port in 1..65535 && callback.rawQuery == null && callback.fragment == null && callback.userInfo == null) {
                "Invalid ChatGPT loopback callback"
            }
            require(hostId.startsWith("urn:uuid:")) { "A stable installation host ID is required" }
            require(previous == null || previous.profileId == profileId) { "The registration belongs to another profile" }
            val keys = Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 32)
            val nonce = keys.generateKey()
            val parameters = mutableMapOf<String, Any>("resource" to RESOURCE, "ext_agent_host_id" to hostId, "nonce" to nonce)
            if (previous == null) {
                parameters["agent_name_hint"] = "Visual Agent"
            } else if (previous.idToken.isNotBlank()) {
                parameters["id_token_hint"] = previous.idToken
            }
            val builder =
                OAuth2AuthorizationRequest
                    .authorizationCode()
                    .authorizationUri("$ISSUER/api/accounts/authorize")
                    .clientId(previous?.clientId ?: INITIAL_CLIENT)
                    .redirectUri(callback.toString())
                    .scopes(SCOPES)
                    .state(keys.generateKey())
                    .additionalParameters(parameters)
            OAuth2AuthorizationRequestCustomizers.withPkce().accept(builder)
            return DirectOAuthAttempt(
                profileId,
                builder.build(),
                nonce,
                clock.instant().plusSeconds(300),
                previous?.clientId,
                previous?.subject,
            )
        }
    }
}
