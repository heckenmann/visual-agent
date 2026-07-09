package de.heckenmann.visualagent.config

import de.heckenmann.visualagent.config.ThemeMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class AppConfigTest {
    @Test
    fun `save persists settings to user_preferences table`() {
        val config = AppConfig.instance
        val original = snapshot(config)
        val tempDb = createTempDirectory("visual-agent-config-test").resolve("settings.db").toString()
        val propertiesFile = File("src/main/resources/config/app.properties")
        val propertiesBefore = propertiesFile.readText()

        try {
            val boundDb =
                de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                    .create(tempDb)
            config.bindPreferenceStore(boundDb)
            config.databasePath = tempDb
            config.uiThemeMode = ThemeMode.DARK
            config.fontSize = 18
            config.llmProvider = "openai"
            config.ollamaModel = "llama3.2:3b"
            config.ollamaApiKey = "ollama-test"
            config.openAiApiKey = "sk-test"
            config.openAiBaseUrl = "https://openai-compatible.example"
            config.openAiModel = "gpt-test"
            config.contextLength = 8192
            config.streamingEnabled = false
            config.autoCompactionEnabled = false
            config.userModelInstruction = "Always respond in German."
            config.save()

            val db =
                de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                    .create(tempDb)
            assertEquals(ThemeMode.DARK.name, db.getPreference("ui.theme.mode"))
            assertEquals("18", db.getPreference("ui.font.size"))
            assertEquals("openai", db.getPreference("llm.provider"))
            assertEquals("llama3.2:3b", db.getPreference("ollama.model"))
            assertEquals("ollama-test", db.getPreference("ollama.api.key"))
            assertEquals("sk-test", db.getPreference("openai.api.key"))
            assertEquals("https://openai-compatible.example", db.getPreference("openai.base.url"))
            assertEquals("gpt-test", db.getPreference("openai.model"))
            assertEquals("8192", db.getPreference("session.context.length"))
            assertEquals("false", db.getPreference("session.streaming.enabled"))
            assertEquals("false", db.getPreference("session.auto.compaction.enabled"))
            assertEquals("Always respond in German.", db.getPreference("session.user.model.instruction"))
            assertNull(db.getPreference("database.path"))
            assertEquals(propertiesBefore, propertiesFile.readText())
            assertTrue(File(tempDb).exists())
        } finally {
            restore(config, original)
        }
    }

    @Test
    fun `reload restores settings from database`() {
        val config = AppConfig.instance
        val original = snapshot(config)
        val tempDb = createTempDirectory("visual-agent-config-reload-test").resolve("settings.db").toString()

        try {
            val boundDb =
                de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                    .create(tempDb)
            config.bindPreferenceStore(boundDb)
            config.databasePath = tempDb
            config.uiThemeMode = ThemeMode.LIGHT
            config.fontSize = 20
            config.llmProvider = "openai"
            config.ollamaApiKey = "ollama-reload"
            config.openAiApiKey = "sk-reload"
            config.openAiBaseUrl = "https://reload.example"
            config.openAiModel = "gpt-reload"
            config.timeoutSeconds = 240
            config.maxParallelSubAgents = 7
            config.userModelInstruction = "Use concise answers."
            config.save()

            // Simulate in-memory drift before "next startup"/reload
            config.uiThemeMode = ThemeMode.SYSTEM
            config.fontSize = 12
            config.llmProvider = "ollama"
            config.ollamaApiKey = ""
            config.openAiApiKey = ""
            config.openAiBaseUrl = "https://api.openai.com"
            config.openAiModel = "gpt-4o-mini"
            config.timeoutSeconds = 60
            config.maxParallelSubAgents = 2
            config.userModelInstruction = ""

            config.reload()

            assertEquals(ThemeMode.LIGHT, config.uiThemeMode)
            assertEquals(20, config.fontSize)
            assertEquals("openai", config.normalizedProvider())
            assertEquals("ollama-reload", config.ollamaApiKey)
            assertEquals("sk-reload", config.openAiApiKey)
            assertEquals("https://reload.example", config.openAiBaseUrl)
            assertEquals("gpt-reload", config.openAiModel)
            assertEquals(240, config.timeoutSeconds)
            assertEquals(7, config.maxParallelSubAgents)
            assertEquals("Use concise answers.", config.userModelInstruction)
        } finally {
            restore(config, original)
        }
    }

    @Test
    fun `binding preference store immediately restores settings for application startup`() {
        val config = AppConfig.instance
        val original = snapshot(config)
        val tempDb = createTempDirectory("visual-agent-config-bind-test").resolve("settings.db").toString()

        try {
            val boundDb =
                de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                    .create(tempDb)
            boundDb.setPreference("openai.api.key", "sk-startup")
            boundDb.setPreference("ollama.api.key", "ollama-startup")
            boundDb.setPreference("openai.base.url", "https://startup.example")
            boundDb.setPreference("openai.model", "gpt-startup")

            config.openAiApiKey = ""
            config.ollamaApiKey = ""
            config.openAiBaseUrl = "https://api.openai.com"
            config.openAiModel = "gpt-4o-mini"
            config.bindPreferenceStore(boundDb)

            assertEquals("sk-startup", config.openAiApiKey)
            assertEquals("ollama-startup", config.ollamaApiKey)
            assertEquals("https://startup.example", config.openAiBaseUrl)
            assertEquals("gpt-startup", config.openAiModel)
        } finally {
            restore(config, original)
        }
    }

    @Test
    fun `save notifies observers for changed model`() {
        val config = AppConfig.instance
        val original = snapshot(config)
        val tempDb = createTempDirectory("visual-agent-config-observer-test").resolve("settings.db").toString()
        val changes = mutableListOf<AppConfigChange>()
        val registration = config.addChangeListener { changes.add(it) }

        try {
            val boundDb =
                de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                    .create(tempDb)
            config.bindPreferenceStore(boundDb)
            config.databasePath = tempDb
            config.ollamaModel = "observer-model"
            config.save()

            assertTrue(changes.any { it.key == "ollama.model" && it.newValue == "observer-model" })
        } finally {
            registration.close()
            restore(config, original)
        }
    }

    private data class ConfigSnapshot(
        val databasePath: String,
        val ollamaLocalUrl: String,
        val llmProvider: String,
        val ollamaModel: String,
        val ollamaApiKey: String,
        val openAiApiKey: String,
        val openAiBaseUrl: String,
        val openAiModel: String,
        val uiThemeMode: ThemeMode,
        val fontSize: Int,
        val browserDefault: String,
        val contextLength: Int,
        val streamingEnabled: Boolean,
        val thinkingEnabled: Boolean,
        val autoCompactionEnabled: Boolean,
        val loadLimit: Int,
        val maxParallelSubAgents: Int,
        val timeoutSeconds: Int,
        val userModelInstruction: String,
    )

    private fun snapshot(config: AppConfig): ConfigSnapshot =
        ConfigSnapshot(
            databasePath = config.databasePath,
            ollamaLocalUrl = config.ollamaLocalUrl,
            llmProvider = config.llmProvider,
            ollamaModel = config.ollamaModel,
            ollamaApiKey = config.ollamaApiKey,
            openAiApiKey = config.openAiApiKey,
            openAiBaseUrl = config.openAiBaseUrl,
            openAiModel = config.openAiModel,
            uiThemeMode = config.uiThemeMode,
            fontSize = config.fontSize,
            browserDefault = config.browserDefault,
            contextLength = config.contextLength,
            streamingEnabled = config.streamingEnabled,
            thinkingEnabled = config.thinkingEnabled,
            autoCompactionEnabled = config.autoCompactionEnabled,
            loadLimit = config.loadLimit,
            maxParallelSubAgents = config.maxParallelSubAgents,
            timeoutSeconds = config.timeoutSeconds,
            userModelInstruction = config.userModelInstruction,
        )

    private fun restore(
        config: AppConfig,
        snapshot: ConfigSnapshot,
    ) {
        config.databasePath = snapshot.databasePath
        config.ollamaLocalUrl = snapshot.ollamaLocalUrl
        config.llmProvider = snapshot.llmProvider
        config.ollamaModel = snapshot.ollamaModel
        config.ollamaApiKey = snapshot.ollamaApiKey
        config.openAiApiKey = snapshot.openAiApiKey
        config.openAiBaseUrl = snapshot.openAiBaseUrl
        config.openAiModel = snapshot.openAiModel
        config.uiThemeMode = snapshot.uiThemeMode
        config.fontSize = snapshot.fontSize
        config.browserDefault = snapshot.browserDefault
        config.contextLength = snapshot.contextLength
        config.streamingEnabled = snapshot.streamingEnabled
        config.thinkingEnabled = snapshot.thinkingEnabled
        config.autoCompactionEnabled = snapshot.autoCompactionEnabled
        config.loadLimit = snapshot.loadLimit
        config.maxParallelSubAgents = snapshot.maxParallelSubAgents
        config.timeoutSeconds = snapshot.timeoutSeconds
        config.userModelInstruction = snapshot.userModelInstruction
        config.save()
    }
}
