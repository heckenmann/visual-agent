package de.heckenmann.visualagent.config

import de.heckenmann.visualagent.config.ThemeMode
import org.junit.jupiter.api.Test
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppConfigPropertiesTest {
    @Test
    fun `bootstrap properties only contain database path`() {
        val config = AppConfig.instance
        val originalPath = config.databasePath

        val properties = AppConfigProperties.bootstrapFrom(config)

        assertEquals(originalPath, properties[AppConfig.KEY_DATABASE_PATH])
        assertEquals(1, properties.size)
    }

    @Test
    fun `apply bootstrap loads database path`() {
        val config = AppConfig.instance
        val originalPath = config.databasePath
        val properties = Properties()
        properties.setProperty(AppConfig.KEY_DATABASE_PATH, "./data/custom.db")

        AppConfigProperties.applyBootstrapTo(config, properties)

        assertEquals("./data/custom.db", config.databasePath)
        config.databasePath = originalPath
    }

    @Test
    fun `export properties contain all session keys`() {
        val config = AppConfig.instance
        config.contextLength = 8192
        config.maxParallelSubAgents = 8

        val properties = AppConfigProperties.exportFrom(config)

        assertTrue(properties.containsKey(AppConfig.KEY_LLM_PROVIDER))
        assertTrue(properties.containsKey(AppConfig.KEY_SESSION_CONTEXT_LENGTH))
        assertEquals("8192", properties[AppConfig.KEY_SESSION_CONTEXT_LENGTH])
        assertEquals("8", properties[AppConfig.KEY_SESSION_MAX_PARALLEL_SUB_AGENTS])
    }

    @Test
    fun `applyTo loads all properties into config`() {
        val config = AppConfig.instance
        val snapshot = snapshot(config)
        val properties = Properties()
        properties.setProperty(AppConfig.KEY_LLM_PROVIDER, "openai")
        properties.setProperty(AppConfig.KEY_OLLAMA_MODEL, "test-model")
        properties.setProperty(AppConfig.KEY_OPENAI_BASE_URL, "https://openai.test")
        properties.setProperty(AppConfig.KEY_OPENAI_MODEL, "gpt-test")
        properties.setProperty(AppConfig.KEY_UI_THEME_MODE, "DARK")
        properties.setProperty(AppConfig.KEY_UI_FONT_SIZE, "18")
        properties.setProperty(AppConfig.KEY_BROWSER_DEFAULT, "chromium")
        properties.setProperty(AppConfig.KEY_SESSION_CONTEXT_LENGTH, "2048")
        properties.setProperty(AppConfig.KEY_SESSION_STREAMING, "false")
        properties.setProperty(AppConfig.KEY_SESSION_THINKING, "true")
        properties.setProperty(AppConfig.KEY_SESSION_AUTO_COMPACTION, "false")
        properties.setProperty(AppConfig.KEY_SESSION_LOAD_LIMIT, "100")
        properties.setProperty(AppConfig.KEY_SESSION_MAX_PARALLEL_SUB_AGENTS, "2")
        properties.setProperty(AppConfig.KEY_SESSION_TIMEOUT_SECONDS, "60")
        properties.setProperty(AppConfig.KEY_SESSION_USER_MODEL_INSTRUCTION, "Be short")
        properties.setProperty(AppConfig.KEY_SESSION_FAVORITE_MODELS, "a,b")

        AppConfigProperties.applyTo(config, properties)

        assertEquals("openai", config.llmProvider)
        assertEquals("test-model", config.ollamaModel)
        assertEquals("https://openai.test", config.openAiBaseUrl)
        assertEquals("gpt-test", config.openAiModel)
        assertEquals(ThemeMode.DARK, config.uiThemeMode)
        assertEquals(18, config.fontSize)
        assertEquals("chromium", config.browserDefault)
        assertEquals(2048, config.contextLength)
        assertEquals(false, config.streamingEnabled)
        assertEquals(true, config.thinkingEnabled)
        assertEquals(false, config.autoCompactionEnabled)
        assertEquals(100, config.loadLimit)
        assertEquals(2, config.maxParallelSubAgents)
        assertEquals(60, config.timeoutSeconds)
        assertEquals("Be short", config.userModelInstruction)
        assertEquals("a,b", config.favoriteModels)

        restore(config, snapshot)
    }

    @Test
    fun `applyTo clamps int values to range`() {
        val config = AppConfig.instance
        val snapshot = snapshot(config)
        val properties = Properties()
        properties.setProperty(AppConfig.KEY_UI_FONT_SIZE, "99")
        properties.setProperty(AppConfig.KEY_SESSION_TIMEOUT_SECONDS, "1")

        AppConfigProperties.applyTo(config, properties)

        assertEquals(24, config.fontSize)
        assertEquals(5, config.timeoutSeconds)

        restore(config, snapshot)
    }

    @Test
    fun `applyTo falls back for missing keys`() {
        val config = AppConfig.instance
        val snapshot = snapshot(config)
        config.uiThemeMode = ThemeMode.LIGHT

        AppConfigProperties.applyTo(config, Properties())

        assertEquals(ThemeMode.LIGHT, config.uiThemeMode)

        restore(config, snapshot)
    }

    private fun snapshot(config: AppConfig): Map<String, String> =
        AppConfigProperties.exportFrom(config).map { it.key.toString() to it.value.toString() }.toMap()

    private fun restore(
        config: AppConfig,
        snapshot: Map<String, String>,
    ) {
        val properties = Properties()
        snapshot.forEach { (key, value) -> properties.setProperty(key, value) }
        AppConfigProperties.applyTo(config, properties)
    }
}
