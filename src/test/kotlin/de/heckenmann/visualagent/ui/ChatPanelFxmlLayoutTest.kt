package de.heckenmann.visualagent.ui

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatPanelFxmlLayoutTest {
    @Test
    fun `chat panel fxml keeps single global status location and local todo info`() {
        val res = javaClass.getResourceAsStream("/fxml/chat-panel.fxml")?.bufferedReader()?.use { it.readText() }
        assertTrue(res != null && res.contains("chat-session-info-bar"), "chat-panel.fxml must include the session info bar")
        assertTrue(res != null && !res.contains("fx:id=\"connectionInfoLabel\""), "chat-panel.fxml must not duplicate connection status")
        assertTrue(res != null && !res.contains("fx:id=\"modelInfoLabel\""), "chat-panel.fxml must not duplicate selected model")
        assertTrue(res != null && !res.contains("fx:id=\"agentsInfoLabel\""), "chat-panel.fxml must not duplicate agent count")
        assertTrue(res != null && res.contains("fx:id=\"todoInfoLabel\""), "chat-panel.fxml must define todo summary label")
        assertTrue(res != null && res.contains("fx:id=\"openFileButton\""), "chat-panel.fxml must define workspace file import button")
        assertTrue(
            res != null && !res.contains("fx:id=\"assistantBusySpinner\""),
            "chat-panel.fxml must not define a duplicate runtime spinner",
        )
        assertTrue(res != null && res.contains("fx:id=\"assistantBusyLabel\""), "chat-panel.fxml must define assistant busy label")
        assertTrue(
            res != null && res.contains("fx:id=\"conversationIconImage\""),
            "chat-panel.fxml must use the application icon in the conversation header",
        )
    }

    @Test
    fun `main window fxml defines files navigation button`() {
        val res = javaClass.getResourceAsStream("/fxml/main-window.fxml")?.bufferedReader()?.use { it.readText() }

        assertTrue(res != null && res.contains("fx:id=\"filesBtn\""), "main-window.fxml must define files navigation")
    }

    @Test
    fun `chat styles hide horizontal scrollbar`() {
        val res = javaClass.getResourceAsStream("/styles/application.css")?.bufferedReader()?.use { it.readText() }
        assertTrue(
            res != null && res.contains(".chat-message-list-no-hbar .scroll-bar:horizontal"),
            "application.css must suppress the chat horizontal scrollbar",
        )
    }
}
