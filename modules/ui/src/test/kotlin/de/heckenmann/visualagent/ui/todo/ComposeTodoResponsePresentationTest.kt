package de.heckenmann.visualagent.ui.todo

import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies bounded tail rendering and execution correlation for todo responses. */
class ComposeTodoResponsePresentationTest {
    @Test
    fun `tail keeps only newest lines`() {
        assertEquals("…\nline 3\nline 4", todoResponseTail("line 1\nline 2\nline 3\nline 4", maxLines = 2))
    }

    @Test
    fun `new execution replaces stale response while same execution appends`() {
        val state = TodoResponseState()

        state.apply("attempt-1", "agent-a", "first", completed = false)
        state.apply("attempt-1", "agent-a", " second", completed = false)
        assertEquals("first second", state.text)

        state.apply("attempt-2", "agent-a", "retry", completed = false)
        assertEquals("retry", state.text)
    }
}
