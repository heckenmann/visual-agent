package de.heckenmann.visualagent.ui

import de.heckenmann.visualagent.ui.panels.TodoPanel
import javafx.scene.input.KeyCode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TodoPanelShortcutTest {
    @Test
    fun `plain n does not open add todo dialog`() {
        assertFalse(TodoPanel.shouldOpenAddDialog(KeyCode.N, shortcutDown = false, focusOwnerIsTextInput = false))
    }

    @Test
    fun `shortcut n opens add todo dialog outside text input`() {
        assertTrue(TodoPanel.shouldOpenAddDialog(KeyCode.N, shortcutDown = true, focusOwnerIsTextInput = false))
    }

    @Test
    fun `shortcut n is ignored inside text input`() {
        assertFalse(TodoPanel.shouldOpenAddDialog(KeyCode.N, shortcutDown = true, focusOwnerIsTextInput = true))
    }
}
