package de.heckenmann.visualagent.config

import java.util.Properties

/**
 * Converts application configuration to and from non-secret properties.
 */
internal object AppConfigProperties {
    fun bootstrapFrom(config: AppConfig): Properties =
        Properties().apply {
            setProperty(AppConfig.KEY_DATABASE_PATH, config.databasePath)
        }

    fun exportFrom(config: AppConfig): Properties =
        Properties().apply {
            setProperty(AppConfig.KEY_LLM_PROVIDER, config.llmProvider)
            setProperty(AppConfig.KEY_OLLAMA_LOCAL_URL, config.ollamaLocalUrl)
            setProperty(AppConfig.KEY_OLLAMA_MODEL, config.ollamaModel)
            setProperty(AppConfig.KEY_OPENAI_BASE_URL, config.openAiBaseUrl)
            setProperty(AppConfig.KEY_OPENAI_MODEL, config.openAiModel)
            setProperty(AppConfig.KEY_DATABASE_PATH, config.databasePath)
            setProperty(AppConfig.KEY_UI_THEME_MODE, config.uiThemeMode.name)
            setProperty(AppConfig.KEY_UI_FONT_SIZE, config.fontSize.toString())
            setProperty(AppConfig.KEY_BROWSER_DEFAULT, config.browserDefault)
            setProperty(AppConfig.KEY_SESSION_FAVORITE_MODELS, config.favoriteModels)
            setProperty(AppConfig.KEY_SESSION_CONTEXT_LENGTH, config.contextLength.toString())
            setProperty(AppConfig.KEY_SESSION_STREAMING, config.streamingEnabled.toString())
            setProperty(AppConfig.KEY_SESSION_THINKING, config.thinkingEnabled.toString())
            setProperty(AppConfig.KEY_SESSION_AUTO_COMPACTION, config.autoCompactionEnabled.toString())
            setProperty(AppConfig.KEY_SESSION_LOAD_LIMIT, config.loadLimit.toString())
            setProperty(AppConfig.KEY_SESSION_MAX_PARALLEL_SUB_AGENTS, config.maxParallelSubAgents.toString())
            setProperty(AppConfig.KEY_SESSION_TIMEOUT_SECONDS, config.timeoutSeconds.toString())
            setProperty(AppConfig.KEY_SESSION_USER_MODEL_INSTRUCTION, config.userModelInstruction)
        }

    fun applyBootstrapTo(
        config: AppConfig,
        properties: Properties,
    ) {
        config.databasePath = properties.string(AppConfig.KEY_DATABASE_PATH, config.databasePath)
    }

    fun applyTo(
        config: AppConfig,
        properties: Properties,
    ) {
        config.llmProvider = properties.string(AppConfig.KEY_LLM_PROVIDER, config.llmProvider)
        config.ollamaLocalUrl = properties.string(AppConfig.KEY_OLLAMA_LOCAL_URL, config.ollamaLocalUrl)
        config.ollamaModel = properties.string(AppConfig.KEY_OLLAMA_MODEL, config.ollamaModel)
        config.openAiBaseUrl = properties.string(AppConfig.KEY_OPENAI_BASE_URL, config.openAiBaseUrl)
        config.openAiModel = properties.string(AppConfig.KEY_OPENAI_MODEL, config.openAiModel)
        config.databasePath = properties.string(AppConfig.KEY_DATABASE_PATH, config.databasePath)
        config.uiThemeMode = properties.themeMode(AppConfig.KEY_UI_THEME_MODE, config.uiThemeMode)
        config.fontSize = properties.int(AppConfig.KEY_UI_FONT_SIZE, config.fontSize, 10..24)
        config.browserDefault = properties.string(AppConfig.KEY_BROWSER_DEFAULT, config.browserDefault)
        config.contextLength = properties.int(AppConfig.KEY_SESSION_CONTEXT_LENGTH, config.contextLength, 1024..32768)
        config.streamingEnabled = properties.boolean(AppConfig.KEY_SESSION_STREAMING, config.streamingEnabled)
        config.thinkingEnabled = properties.boolean(AppConfig.KEY_SESSION_THINKING, config.thinkingEnabled)
        config.autoCompactionEnabled =
            properties.boolean(AppConfig.KEY_SESSION_AUTO_COMPACTION, config.autoCompactionEnabled)
        config.loadLimit = properties.int(AppConfig.KEY_SESSION_LOAD_LIMIT, config.loadLimit, 1..1000)
        config.maxParallelSubAgents =
            properties.int(AppConfig.KEY_SESSION_MAX_PARALLEL_SUB_AGENTS, config.maxParallelSubAgents, 1..20)
        config.timeoutSeconds = properties.int(AppConfig.KEY_SESSION_TIMEOUT_SECONDS, config.timeoutSeconds, 5..600)
        config.userModelInstruction =
            properties.string(AppConfig.KEY_SESSION_USER_MODEL_INSTRUCTION, config.userModelInstruction)
        config.favoriteModels = properties.string(AppConfig.KEY_SESSION_FAVORITE_MODELS, config.favoriteModels)
    }

    private fun Properties.string(
        key: String,
        fallback: String,
    ): String = getProperty(key, fallback)

    private fun Properties.themeMode(
        key: String,
        fallback: ThemeMode,
    ): ThemeMode = getProperty(key)?.let(ThemeMode::fromString) ?: fallback

    private fun Properties.int(
        key: String,
        fallback: Int,
        range: IntRange,
    ): Int = getProperty(key, fallback.toString()).toIntOrNull()?.coerceIn(range) ?: fallback

    private fun Properties.boolean(
        key: String,
        fallback: Boolean,
    ): Boolean = getProperty(key, fallback.toString()).toBooleanStrictOrNull() ?: fallback
}
