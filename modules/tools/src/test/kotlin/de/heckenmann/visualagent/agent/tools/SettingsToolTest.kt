package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolSettings
import de.heckenmann.visualagent.agent.tools.api.ToolSettingsPort
import de.heckenmann.visualagent.agent.tools.api.ToolSettingsUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies UI-scale parsing and output of the model-facing settings tool. */
class SettingsToolTest {
    @Test
    fun `settings tool supports manual and automatic UI scale`() {
        val settings = RecordingSettingsPort()
        val tool = SettingsTool(settings)

        val manual = tool.execute("""{"action":"set","uiScalePercent":225}""")
        val automatic = tool.execute("""{"action":"set","uiScalePercent":0}""")

        assertEquals(200, settings.updates.first().uiScalePercent)
        assertEquals(0, settings.updates.last().uiScalePercent)
        assertTrue(manual.content.contains("UI scale: 200%"))
        assertTrue(automatic.content.contains("UI scale: Automatic"))
    }

    private class RecordingSettingsPort : ToolSettingsPort {
        var current = defaultSettings()
        val updates = mutableListOf<ToolSettingsUpdate>()

        override fun read(): ToolSettings = current

        override fun update(update: ToolSettingsUpdate): ToolSettings {
            updates += update
            current = current.copy(uiScalePercent = update.uiScalePercent?.takeIf { it != 0 })
            return current
        }
    }

    private companion object {
        fun defaultSettings() =
            ToolSettings(
                fontSize = 14,
                provider = "ollama",
                model = "test-model",
                openAiBaseUrl = "https://api.openai.com",
                openAiApiKeyConfigured = false,
                streamingEnabled = true,
                thinkingEnabled = false,
                timeoutSeconds = 120,
                uiScalePercent = null,
            )
    }
}
