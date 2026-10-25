package de.heckenmann.visualagent.ui.conversation.live

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveTextTransitionTest {
    @Test
    fun `only changed words and digits are replacement slots`() {
        val before = "Still downloading file 12"
        val after = "Still processing file 11"
        val pieces = liveTextPieces(before, after)!!
        assertEquals(before, pieces.joinToString("") { it.previous })
        assertEquals(after, pieces.joinToString("") { it.current })
        assertEquals(listOf(LiveTextPiece("downloading", "processing"), LiveTextPiece("2", "1")), pieces.filter { it.changed })
        assertEquals(listOf(LiveTextPiece("", "1"), LiveTextPiece("9", "0"), LiveTextPiece("9", "0")), liveTextPieces("99", "100"))
    }

    @Test
    fun `tokenization preserves whitespace non-latin words and grapheme clusters`() {
        val text = "Prüfe e\u0301 👨‍👩‍👧‍👦\n文件 ١٢"
        val pieces = liveTextPieces(text, text.replace("١٢", "١١"))!!
        assertEquals(text, pieces.joinToString("") { it.previous })
        assertEquals(listOf(LiveTextPiece("٢", "١")), pieces.filter { it.changed })
        assertTrue(pieces.any { it.current == "👨‍👩‍👧‍👦" })
        assertNull(liveTextPieces("a ".repeat(257), "b ".repeat(257)))
    }

    @Test
    fun `updates during a transition replace a single pending target rather than enqueueing`() {
        val transition = LiveTextTransition("Initial")
        transition.offer("Initial", false)
        assertFalse(transition.running)
        val initiallyEmpty = LiveTextTransition("")
        initiallyEmpty.offer("First visible value", false)
        assertFalse(initiallyEmpty.running)
        transition.offer("First", false)
        transition.offer("Obsolete", false)
        transition.offer("Latest", false)
        assertEquals(1L, transition.revision)
        assertEquals("First", transition.current)
        transition.complete()
        assertEquals("First", transition.previous)
        assertEquals("Latest", transition.current)
        assertEquals(2L, transition.revision)
        transition.complete()
        assertFalse(transition.running)
    }

    @Test
    fun `terminal updates take priority and disabled motion settles the latest value`() {
        val transition = LiveTextTransition("Initial")
        transition.offer("Running", false)
        transition.offer("Completed", true)
        transition.offer("Late running state", false)
        transition.settle()
        assertEquals("Completed", transition.current)
        assertFalse(transition.running)
        transition.offer("Obsolete", false)
        assertEquals("Completed", transition.current)
    }
}
