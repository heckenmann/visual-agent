package de.heckenmann.visualagent.agent.provider

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ProviderErrorMessagesTest {
    @Test
    fun `subscription error is translated without exposing raw response`() {
        val message =
            ProviderErrorMessages.userFacing(
                IllegalStateException("HTTP 403 - {\"error\":\"this model requires a subscription, upgrade for access\"}"),
            )

        assertContains(message, "not available")
        assertFalse(message.contains("{\"error\""))
    }

    @Test
    fun `quota error provides billing action`() {
        val message = ProviderErrorMessages.userFacing(IllegalStateException("429 current quota exceeded"))

        assertContains(message, "quota")
        assertContains(message, "billing")
    }

    @Test
    fun `authentication timeout connection and fallback errors are actionable`() {
        assertContains(ProviderErrorMessages.userFacing(IllegalStateException("401 unauthorized")), "Authentication")
        assertContains(ProviderErrorMessages.userFacing(IllegalStateException("request timed out")), "timeout")
        assertContains(ProviderErrorMessages.userFacing(IllegalStateException("DNS unknown host")), "reached")
        assertEquals(
            "The model request failed. Check the active provider and model, then try again.",
            ProviderErrorMessages.userFacing(IllegalStateException("unexpected failure")),
        )
    }

    @Test
    fun `nested causes contribute to provider classification`() {
        val error = IllegalStateException("request failed", IllegalArgumentException("invalid api key"))

        assertContains(ProviderErrorMessages.userFacing(error), "Authentication")
    }
}
