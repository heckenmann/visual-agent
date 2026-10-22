package de.heckenmann.visualagent.agent.codexdirect

import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class DirectOAuthRevocationTest {
    @Test
    fun `discovery cannot redirect refresh credentials to a different origin`() {
        listOf(
            "https://other.example/revoke",
            "https://auth.openai.com:8443/revoke",
            "http://auth.openai.com/revoke",
            "https://user@auth.openai.com/revoke",
            "https://auth.openai.com/revoke#fragment",
        ).forEach { endpoint ->
            val posts = AtomicInteger()
            val protocol = protocol(endpoint, posts)

            StepVerifier.create(protocol.revoke(record())).expectError(IllegalArgumentException::class.java).verify()

            assertEquals(0, posts.get())
        }
    }

    @Test
    fun `same origin revocation accepts implicit and explicit https default ports`() {
        listOf("https://auth.openai.com/revoke", "https://auth.openai.com:443/revoke").forEach { endpoint ->
            val posts = AtomicInteger()

            StepVerifier.create(protocol(endpoint, posts).revoke(record())).expectNext(true).verifyComplete()

            assertEquals(1, posts.get())
        }
    }

    private fun protocol(
        endpoint: String,
        posts: AtomicInteger,
    ): DirectOAuthProtocol =
        DirectOAuthProtocol(
            WebClient
                .builder()
                .exchangeFunction { request ->
                    if (request.method() == HttpMethod.POST) {
                        assertEquals(endpoint, request.url().toString())
                        posts.incrementAndGet()
                        Mono.just(ClientResponse.create(HttpStatus.OK).build())
                    } else {
                        assertEquals("${DirectOAuthAttempt.ISSUER}/.well-known/openid-configuration", request.url().toString())
                        Mono.just(
                            ClientResponse
                                .create(HttpStatus.OK)
                                .header("Content-Type", "application/json")
                                .body("""{"revocation_endpoint":"$endpoint"}""")
                                .build(),
                        )
                    }
                }.build(),
        )

    private fun record() =
        DirectOAuthCredentials(
            "profile",
            "client",
            "subject",
            DirectOAuthAttempt.ISSUER,
            "synthetic-access",
            "synthetic-refresh",
            "synthetic-identity",
            1,
            setOf("chatgpt.tokens.use.direct"),
        )
}
