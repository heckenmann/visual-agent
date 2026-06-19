package de.heckenmann.visualagent.ui

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApplicationSettingsFxmlTest {
    @Test
    fun `settings fxml does not contain back button`() {
        val res = javaClass.getResourceAsStream("/fxml/application-settings.fxml")?.bufferedReader()?.use { it.readText() }
        assertTrue(
            res != null && !res.contains("fx:id=\"btnBack\""),
            "application-settings.fxml must not contain a Button with fx:id=\"btnBack\"",
        )
    }

    @Test
    fun `settings fxml uses responsive page cards`() {
        val res = javaClass.getResourceAsStream("/fxml/application-settings.fxml")?.bufferedReader()?.use { it.readText() }
        assertTrue(
            res != null &&
                res.contains("styleClass=\"page-root\"") &&
                res.contains("styleClass=\"settings-card\"") &&
                !res.contains("prefWidth=\"260.0\""),
            "application-settings.fxml must use responsive shared page cards",
        )
    }
}
