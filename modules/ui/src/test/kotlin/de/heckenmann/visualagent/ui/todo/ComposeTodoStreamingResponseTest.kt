package de.heckenmann.visualagent.ui.todo

import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests the fixed-width response window used by processing todo rows. */
class ComposeTodoStreamingResponseTest {
    @Test
    fun `fitted suffix keeps newest text and removes older left side`() {
        assertEquals("3456", fittedTextSuffix("123456", availableWidthPx = 4) { it.length })
    }

    @Test
    fun `fitted suffix keeps short responses unchanged`() {
        assertEquals("short", fittedTextSuffix("short", availableWidthPx = 20) { it.length })
    }

    @Test
    fun `fitted suffix preserves a complete emoji`() {
        assertEquals("🙂", fittedTextSuffix("old🙂", availableWidthPx = 2) { it.length })
    }

    @Test
    fun `streaming line keeps the newest text across line breaks`() {
        assertEquals("first latest", todoStreamingLine("first\nlatest"))
    }
}
