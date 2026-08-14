package de.heckenmann.visualagent.ui.todo

import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests the fixed-width response window used by processing todo rows. */
class ComposeTodoStreamingResponseTest {
    @Test
    fun `response window keeps newest text and removes older left side`() {
        assertEquals("3456", todoResponseWindow("123456", maxChars = 4))
    }

    @Test
    fun `response window keeps short responses unchanged`() {
        assertEquals("short", todoResponseWindow("short", maxChars = 20))
    }
}
