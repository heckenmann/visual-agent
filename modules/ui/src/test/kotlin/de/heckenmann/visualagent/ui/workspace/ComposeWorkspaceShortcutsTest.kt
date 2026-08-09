@file:Suppress("ktlint:standard:no-wildcard-imports")

package de.heckenmann.visualagent.ui.workspace

import de.heckenmann.visualagent.ui.agents.*
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.canvas.*
import de.heckenmann.visualagent.ui.components.*
import de.heckenmann.visualagent.ui.conversation.*
import de.heckenmann.visualagent.ui.files.*
import de.heckenmann.visualagent.ui.modal.*
import de.heckenmann.visualagent.ui.settings.*
import de.heckenmann.visualagent.ui.status.*
import de.heckenmann.visualagent.ui.todo.*
import de.heckenmann.visualagent.ui.workspace.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ComposeWorkspaceShortcutsTest {
    @Test
    fun `shortcut digits resolve to stable workspace panel ids`() {
        assertEquals("chat", panelIdForShortcutDigit(1))
        assertEquals("todos", panelIdForShortcutDigit(2))
        assertEquals("files", panelIdForShortcutDigit(3))
        assertEquals("agents", panelIdForShortcutDigit(4))
        assertEquals("settings", panelIdForShortcutDigit(5))
        assertEquals("canvas", panelIdForShortcutDigit(6))
    }

    @Test
    fun `unsupported shortcut digits return null`() {
        assertNull(panelIdForShortcutDigit(0))
        assertNull(panelIdForShortcutDigit(7))
    }

    @Test
    fun `command filtering matches title description and id`() {
        val commands =
            listOf(
                ComposeCommand("open-chat", "Open Conversation", "Main agent conversation") {},
                ComposeCommand("open-files", "Open Files", "Workspace import and sync") {},
                ComposeCommand("close-application", "Close application", "Persist workspace state") {},
            )

        assertEquals(listOf("open-chat", "open-files", "close-application"), filterCommands(commands, "").map { it.id })
        assertEquals(listOf("open-files"), filterCommands(commands, "import").map { it.id })
        assertEquals(listOf("close-application"), filterCommands(commands, "close").map { it.id })
        assertEquals(emptyList(), filterCommands(commands, "missing").map { it.id })
    }
}
