package de.heckenmann.visualagent.agent.codex

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** Verifies that native provider text can be forwarded as soon as it arrives. */
class CodexAssistantDeltaBufferTest {
    @Test
    fun `first native delta is immediately available before completion`() {
        val buffer = CodexAssistantDeltaBuffer()

        assertEquals(CodexAssistantTextDelta("Hello", "item-1"), buffer.accept("Hello", "item-1"))
    }
}
