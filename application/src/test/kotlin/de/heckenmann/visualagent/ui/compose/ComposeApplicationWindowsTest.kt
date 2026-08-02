package de.heckenmann.visualagent.ui.compose

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [defaultWindows] in `ComposeApplicationWindows`.
 */
class ComposeApplicationWindowsTest {
    @Test
    fun `default windows cover all shortcut ids`() {
        val windows = defaultWindows()

        assertEquals(workspacePanelShortcutIds, windows.map { it.id })
    }

    @Test
    fun `only chat and todos are visible by default`() {
        val windows = defaultWindows().associateBy { it.id }

        assertTrue(windows.getValue("chat").visible)
        assertTrue(windows.getValue("todos").visible)
        assertFalse(windows.getValue("files").visible)
        assertFalse(windows.getValue("agents").visible)
        assertFalse(windows.getValue("settings").visible)
        assertFalse(windows.getValue("canvas").visible)
    }

    @Test
    fun `chat window has stable title and bounds`() {
        val chat = defaultWindows().first { it.id == "chat" }

        assertEquals("Conversation", chat.title)
        assertEquals(520, chat.bounds.width)
        assertEquals(460, chat.bounds.height)
    }
}
