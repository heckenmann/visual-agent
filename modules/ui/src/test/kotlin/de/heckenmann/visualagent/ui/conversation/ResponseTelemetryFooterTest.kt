package de.heckenmann.visualagent.ui.conversation

import de.heckenmann.visualagent.protocol.ConversationResponseTelemetry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Verifies compact, human-readable presentation of safe response telemetry. */
class ResponseTelemetryFooterTest {
    @Test
    fun `formats available model duration and tokens without diagnostics clutter`() {
        val telemetry = ConversationResponseTelemetry(model = "model", totalMillis = 2_400, totalTokens = 1_240)

        assertEquals("2.4 s · 1.2k tokens", telemetry.summaryLabel())
        assertEquals("84 ms", formatResponseDuration(84))
        assertEquals("2 min 4 s", formatResponseDuration(124_000))
    }

    @Test
    fun `hides footer when no displayable telemetry is available`() {
        assertNull(ConversationResponseTelemetry(model = "model").summaryLabel())
    }
}
