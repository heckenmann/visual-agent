package de.heckenmann.visualagent.ui

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MainWindowFxmlLayoutTest {
    @Test
    fun `main window fxml has native window decorations and model label in header`() {
        val res = javaClass.getResourceAsStream("/fxml/main-window.fxml")?.bufferedReader()?.use { it.readText() }
        assertTrue(res != null && !res.contains("<bottom>"), "main-window.fxml must not render a bottom status bar")
        assertTrue(
            res != null && res.contains("fx:id=\"selectedModelLabel\""),
            "main-window.fxml must expose selectedModelLabel in title bar",
        )
        assertTrue(res != null && res.contains("fx:id=\"appIconImage\""), "main-window.fxml must show the application icon in the header")
        assertTrue(res != null && !res.contains("minimizeButton"), "main-window.fxml must rely on native minimize controls")
        assertTrue(res != null && !res.contains("maximizeButton"), "main-window.fxml must rely on native maximize controls")
        assertTrue(res != null && !res.contains("closeButton"), "main-window.fxml must rely on native close controls")
    }
}
