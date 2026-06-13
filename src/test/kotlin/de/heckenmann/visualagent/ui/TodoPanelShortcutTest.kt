package de.heckenmann.visualagent.ui

import de.heckenmann.visualagent.ui.panels.shouldOpenAddTodoDialog
import javafx.scene.input.KeyCode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TodoPanelShortcutTest {
    @Test
    fun `plain n does not open add todo dialog`() {
        assertFalse(shouldOpenAddTodoDialog(KeyCode.N, shortcutDown = false, focusOwnerIsTextInput = false))
    }

    @Test
    fun `shortcut n opens add todo dialog outside text input`() {
        assertTrue(shouldOpenAddTodoDialog(KeyCode.N, shortcutDown = true, focusOwnerIsTextInput = false))
    }

    @Test
    fun `shortcut n is ignored inside text input`() {
        assertFalse(shouldOpenAddTodoDialog(KeyCode.N, shortcutDown = true, focusOwnerIsTextInput = true))
    }

    @Test
    fun `todo panel fxml contains summary counters`() {
        val res = javaClass.getResourceAsStream("/fxml/todo-panel.fxml")?.bufferedReader()?.use { it.readText() }
        assertTrue(
            res != null &&
                res.contains("fx:id=\"totalCountLabel\"") &&
                res.contains("fx:id=\"openCountLabel\"") &&
                res.contains("fx:id=\"inProgressCountLabel\"") &&
                res.contains("fx:id=\"doneCountLabel\""),
            "todo-panel.fxml must expose summary counters in the header",
        )
    }
}
