package de.heckenmann.visualagent.agent.conversation

import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies that provider chunk boundaries never alter response content. */
class AgentStreamTextTest {
    @Test
    fun `appends provider chunks verbatim`() {
        val collected = StringBuilder()

        appendStreamPart(collected, "Visit https://example.")
        appendStreamPart(collected, "Org/path")

        assertEquals("Visit https://example.Org/path", collected.toString())
    }
}
