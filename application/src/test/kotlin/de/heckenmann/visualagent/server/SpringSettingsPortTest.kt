package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.config.ThemeMode
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import de.heckenmann.visualagent.protocol.ThemeMode as ProtocolThemeMode

/** Verifies that settings exposed to the UI are read from and written to the server bean. */
class SpringSettingsPortTest {
    @Test
    fun `snapshot reflects persisted server configuration`() {
        val config = AppConfigBean()
        config.llmProvider = "openai"
        config.openAiModel = "gpt-test"
        config.uiThemeMode = ThemeMode.DARK
        config.queueFlushMode = "ALL"

        val snapshot = SpringSettingsPort(config).snapshot()

        assertEquals("openai", snapshot.providerId)
        assertEquals("gpt-test", snapshot.modelId)
        assertEquals(ProtocolThemeMode.DARK, snapshot.uiThemeMode)
        assertEquals("ALL", snapshot.queueFlushMode)
    }

    @Test
    fun `save writes the complete settings snapshot to the server bean`() {
        val config = AppConfigBean()
        val port = SpringSettingsPort(config)

        port.save(
            SettingsSnapshot(
                providerId = "openai",
                modelId = "gpt-test",
                uiThemeMode = ProtocolThemeMode.LIGHT,
                queueFlushMode = "ALL",
            ),
        )

        assertEquals("openai", config.llmProvider)
        assertEquals("gpt-test", config.openAiModel)
        assertEquals(ThemeMode.LIGHT, config.uiThemeMode)
        assertEquals("ALL", config.queueFlushMode)
    }
}
