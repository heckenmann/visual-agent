package de.heckenmann.visualagent.agent.codexdirect

import com.openai.errors.OpenAIServiceException
import de.heckenmann.visualagent.agent.provider.ProviderUserFacingException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.ai.openai.responses.OpenAiResponsesException
import org.springframework.http.HttpHeaders
import org.springframework.security.oauth2.core.OAuth2AuthorizationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.nio.charset.StandardCharsets
import java.util.Optional
import java.util.concurrent.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class DirectProviderErrorsTest {
    @Test
    fun `structured stream failures preserve plan and completion categories without provider text`() {
        val expected =
            mapOf(
                "subscription_sharing_usage_limit_exceeded" to "ChatGPT plan usage limit",
                "subscription_sharing_usage_unavailable" to "ChatGPT plan usage unavailable",
                "missing_response_completed" to "Incomplete provider response",
                "response_incomplete" to "Incomplete provider response",
            )
        expected.forEach { (code, summary) ->
            val safe = DirectProviderErrors.safe(OpenAiResponsesException(code, "synthetic-secret-response")) as ProviderUserFacingException

            assertEquals(summary, safe.userFacing.summary)
            assertFalse(safe.userFacing.detail.contains("synthetic-secret-response"))
            assertNull(safe.cause)
        }
    }

    @Test
    fun `http errors produce safe actionable categories without raw bodies or causes`() {
        val expected =
            mapOf(
                401 to "Authentication failed",
                403 to "Access not authorized",
                404 to "Model not available",
                429 to "Provider rate limit",
                422 to "Request not supported",
                503 to "Provider unavailable",
            )
        expected.forEach { (status, summary) ->
            val raw =
                WebClientResponseException.create(
                    status,
                    "secret-response",
                    HttpHeaders(),
                    "secret-response".toByteArray(),
                    StandardCharsets.UTF_8,
                )

            val safe = DirectProviderErrors.safe(raw) as ProviderUserFacingException

            assertEquals(summary, safe.userFacing.summary)
            assertFalse(safe.toString().contains("secret-response"))
            assertFalse(safe.userFacing.detail.contains("secret-response"))
            assertNull(safe.cause)
        }
    }

    @Test
    fun `typed quota exhaustion is distinct from a retryable rate limit`() {
        val raw = mockk<OpenAIServiceException>()
        every { raw.cause } returns null
        every { raw.statusCode() } returns 429
        every { raw.code() } returns Optional.of("insufficient_quota")

        val safe = DirectProviderErrors.safe(raw) as ProviderUserFacingException

        assertEquals("Provider quota exhausted", safe.userFacing.summary)
        assertFalse(safe.userFacing.retryable)
    }

    @Test
    fun `expired refresh grant advises reauthorization without exposing authorization details`() {
        val raw = OAuth2AuthorizationException(OAuth2Error("invalid_grant", "secret-response", null))
        val safe = DirectProviderErrors.safe(raw) as ProviderUserFacingException

        assertEquals("Session expired", safe.userFacing.summary)
        assertNull(safe.cause)
    }

    @Test
    fun `cancellation remains cancellation`() {
        val cancellation = CancellationException("cancelled")
        assertSame(cancellation, DirectProviderErrors.safe(cancellation))
    }
}
