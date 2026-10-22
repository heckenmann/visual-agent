package de.heckenmann.visualagent.agent.codexdirect

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.net.URI
import java.time.Clock
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class DirectOAuthProtocolTest {
    @Test
    fun `callback state is verified before any token request`() {
        val calls = AtomicInteger()
        val attempt = attempt()
        val protocol = DirectOAuthProtocol(client(calls, "chatgpt.tokens.use.direct"))

        StepVerifier
            .create(protocol.exchange(attempt, callback(attempt) + ("state" to "wrong"), Clock.systemUTC()))
            .expectError(IllegalArgumentException::class.java)
            .verify()

        assertEquals(0, calls.get())
    }

    @Test
    fun `identity login without plan permission cannot activate inference`() {
        val calls = AtomicInteger()
        val attempt = attempt()

        StepVerifier
            .create(DirectOAuthProtocol(client(calls, "openid profile")).exchange(attempt, callback(attempt), Clock.systemUTC()))
            .expectErrorMatches { it is IllegalArgumentException && it.message == "ChatGPT plan usage was not authorized" }
            .verify()

        assertEquals(1, calls.get())
    }

    @Test
    fun `malformed identity token is rejected rather than trusted as an account`() {
        val calls = AtomicInteger()
        val attempt = attempt()

        StepVerifier
            .create(
                DirectOAuthProtocol(client(calls, "chatgpt.tokens.use.direct")).exchange(attempt, callback(attempt), Clock.systemUTC()),
            ).expectError(JwtException::class.java)
            .verify()

        assertEquals(1, calls.get())
    }

    private fun client(
        calls: AtomicInteger,
        scope: String,
    ): WebClient =
        WebClient
            .builder()
            .exchangeFunction { request ->
                calls.incrementAndGet()
                assertEquals(DirectOAuthAttempt.TOKEN_ENDPOINT, request.url().toString())
                Mono.just(
                    ClientResponse
                        .create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body(
                            """{"access_token":"synthetic-access","refresh_token":"synthetic-refresh","token_type":"Bearer","expires_in":3600,"scope":"$scope","id_token":"not-a-jwt"}""",
                        ).build(),
                )
            }.build()

    private fun attempt() =
        DirectOAuthAttempt.create(
            "profile",
            "urn:uuid:${UUID.randomUUID()}",
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
