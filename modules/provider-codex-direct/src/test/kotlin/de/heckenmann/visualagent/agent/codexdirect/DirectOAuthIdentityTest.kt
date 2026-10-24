package de.heckenmann.visualagent.agent.codexdirect

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.util.Date
import kotlin.test.assertEquals

class DirectOAuthIdentityTest {
    private val key = RSAKeyGenerator(2048).keyID("test-key").generate()

    @Test
    fun `signed identity from the issuer binds the granted session to the verified account`() {
        val attempt = attempt()
        val protocol = protocol(token(attempt))

        StepVerifier
            .create(protocol.exchange(attempt, callback(attempt), Clock.systemUTC()))
            .assertNext { record ->
                assertEquals("verified-subject", record.subject)
                assertEquals("issued-client", record.clientId)
                assertEquals("profile", record.profileId)
                assertEquals(setOf("chatgpt.tokens.use.direct"), record.scopes)
            }.verifyComplete()
    }

    @Test
    fun `signed but mismatched or expired identity claims cannot authorize a session`() {
        val changes: List<(JWTClaimsSet.Builder) -> Unit> =
            listOf(
                { it.issuer("https://untrusted.example") },
                { it.audience("other-client") },
                { it.claim("nonce", "wrong-nonce") },
                { it.expirationTime(Date.from(Instant.now().minusSeconds(3600))) },
                { it.claim("azp", "other-client") },
            )
        changes.forEach { change ->
            val attempt = attempt()
            StepVerifier
                .create(protocol(token(attempt, change)).exchange(attempt, callback(attempt), Clock.systemUTC()))
                .expectError()
                .verify()
        }
    }

    @Test
    fun `identity signed by a key absent from issuer jwks is rejected`() {
        val attempt = attempt()
        val foreignKey = RSAKeyGenerator(2048).keyID("test-key").generate()
        StepVerifier
            .create(protocol(token(attempt, signer = foreignKey)).exchange(attempt, callback(attempt), Clock.systemUTC()))
            .expectError()
            .verify()
    }

    private fun protocol(idToken: String): DirectOAuthProtocol =
        DirectOAuthProtocol(
            WebClient
                .builder()
                .exchangeFunction { request ->
                    val body =
                        when (request.url().toString()) {
                            DirectOAuthAttempt.TOKEN_ENDPOINT ->
                                """{"access_token":"synthetic-access","refresh_token":"synthetic-refresh","token_type":"Bearer","expires_in":3600,"scope":"chatgpt.tokens.use.direct","id_token":"$idToken"}"""
                            "${DirectOAuthAttempt.ISSUER}/.well-known/jwks.json" -> JWKSet(key.toPublicJWK()).toString()
                            else -> error("Unexpected authorization endpoint")
                        }
                    Mono.just(
                        ClientResponse
                            .create(HttpStatus.OK)
                            .header("Content-Type", "application/json")
                            .body(body)
                            .build(),
                    )
                }.build(),
        )

    private fun token(
        attempt: DirectOAuthAttempt,
        change: (JWTClaimsSet.Builder) -> Unit = {},
        signer: RSAKey = key,
    ): String {
        val claims =
            JWTClaimsSet
                .Builder()
                .issuer(DirectOAuthAttempt.ISSUER)
                .subject("verified-subject")
                .audience("issued-client")
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .claim("nonce", attempt.nonce)
        change(claims)
        return SignedJWT(
            JWSHeader
                .Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .keyID(signer.keyID)
                .build(),
            claims.build(),
        ).apply { sign(RSASSASigner(signer)) }
            .serialize()
    }

    private fun attempt() =
        DirectOAuthAttempt.create(
            "profile",
            "urn:uuid:2ba96fa4-48f6-4ea2-a774-970fe7198320",
            URI("http://127.0.0.1:1455/auth/callback"),
            Clock.systemUTC(),
        )

    private fun callback(attempt: DirectOAuthAttempt) =
        mapOf(
            "state" to requireNotNull(attempt.authorization.state),
            "code" to "synthetic-code",
            "client_id" to "issued-client",
        )
}
