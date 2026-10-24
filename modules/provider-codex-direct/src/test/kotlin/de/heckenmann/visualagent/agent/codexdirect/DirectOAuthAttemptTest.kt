package de.heckenmann.visualagent.agent.codexdirect

import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class DirectOAuthAttemptTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `fresh transactions use different state and framework PKCE challenges`() {
        val first = attempt()
        val second = attempt()

        assertNotEquals(first.authorization.state, second.authorization.state)
        assertNotEquals(first.nonce, second.nonce)
        assertEquals("S256", first.authorization.additionalParameters["code_challenge_method"])
        assertEquals(DirectOAuthAttempt.SCOPES, first.authorization.scopes)
    }

    @Test
    fun `callback must have matching state and can only be consumed once`() {
        val attempt = attempt()
        val parameters = mapOf("state" to requireNotNull(attempt.authorization.state), "code" to "code", "client_id" to "issued-client")
        assertFailsWith<IllegalArgumentException> { attempt.consume(parameters + ("state" to "wrong"), clock) }
        assertEquals("issued-client", attempt.consume(parameters, clock))
        assertFailsWith<IllegalStateException> { attempt.consume(parameters, clock) }
    }

    @Test
    fun `expired attempts fail without exchanging credentials`() {
        val attempt = attempt()
        assertFailsWith<IllegalArgumentException> {
            attempt.consume(
                mapOf("state" to requireNotNull(attempt.authorization.state)),
                Clock.offset(clock, java.time.Duration.ofMinutes(6)),
            )
        }
    }

    private fun attempt() =
        DirectOAuthAttempt.create(
            "profile",
            "urn:uuid:${UUID.randomUUID()}",
            URI("http://127.0.0.1:1455/auth/callback"),
            clock,
        )
}
