package de.heckenmann.visualagent.ui

import de.heckenmann.visualagent.ui.panels.SessionPanel
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionPanelFxmlLayoutTest {
    @Test
    fun `session fxml keeps main scroll pane vertically scrollable`() {
        val res = javaClass.getResourceAsStream("/fxml/session-panel.fxml")?.bufferedReader()?.use { it.readText() }
        assertTrue(
            res != null &&
                res.contains("fx:id=\"scrollPane\"") &&
                res.contains("fitToHeight=\"false\"") &&
                res.contains("vbarPolicy=\"AS_NEEDED\""),
            "session-panel.fxml must keep the main scroll pane vertically scrollable",
        )
    }

    @Test
    fun `session panel region owns layout children override`() {
        val hasOverride = SessionPanel::class.java.declaredMethods.any { it.name == "layoutChildren" }
        assertTrue(hasOverride, "SessionPanel must resize its FXML root in layoutChildren")
    }

    @Test
    fun `session fxml includes user instruction area`() {
        val res = javaClass.getResourceAsStream("/fxml/session-panel.fxml")?.bufferedReader()?.use { it.readText() }
        assertTrue(
            res != null && res.contains("fx:id=\"userInstructionArea\""),
            "session-panel.fxml must include a user instruction text area",
        )
    }
}
