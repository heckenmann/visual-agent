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
}
