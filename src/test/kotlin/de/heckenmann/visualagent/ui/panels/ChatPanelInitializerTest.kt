package de.heckenmann.visualagent.ui.panels

import javafx.scene.input.KeyCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChatPanelInitializerTest {
    @Test
    fun `enter sends the current message`() {
        assertEquals(
            InputKeyboardAction.SEND,
            ChatPanelInitializer.keyboardAction(KeyCode.ENTER, shiftDown = false, shortcutDown = false),
        )
    }

    @Test
    fun `shortcut enter sends the current message`() {
        assertEquals(
            InputKeyboardAction.SEND,
            ChatPanelInitializer.keyboardAction(KeyCode.ENTER, shiftDown = false, shortcutDown = true),
        )
    }

    @Test
    fun `shift enter inserts a line break`() {
        assertEquals(
            InputKeyboardAction.INSERT_LINE_BREAK,
            ChatPanelInitializer.keyboardAction(KeyCode.ENTER, shiftDown = true, shortcutDown = false),
        )
    }

    @Test
    fun `shift wins over shortcut for line breaks`() {
        assertEquals(
            InputKeyboardAction.INSERT_LINE_BREAK,
            ChatPanelInitializer.keyboardAction(KeyCode.ENTER, shiftDown = true, shortcutDown = true),
        )
    }

    @Test
    fun `other keys do not trigger chat input actions`() {
        assertEquals(
            InputKeyboardAction.NONE,
            ChatPanelInitializer.keyboardAction(KeyCode.A, shiftDown = false, shortcutDown = false),
        )
    }
}
