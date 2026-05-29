package de.heckenmann.visualagent.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties

class AppConfigTest {

    @Test
    fun testSaveAndLoad() {
        val config = AppConfig.instance
        val originalTheme = config.theme
        val originalFontSize = config.fontSize

        try {
            // Change values
            config.theme = "Nord Dark"
            config.fontSize = 18
            config.save()

            // Verify file content directly
            val props = Properties()
            File("src/main/resources/config/app.properties").inputStream().use {
                props.load(it)
            }

            assertEquals("Nord Dark", props.getProperty("ui.theme"))
            assertEquals("18", props.getProperty("ui.font.size"))
        } finally {
            // Restore original values
            config.theme = originalTheme
            config.fontSize = originalFontSize
            config.save()
        }
    }
}
