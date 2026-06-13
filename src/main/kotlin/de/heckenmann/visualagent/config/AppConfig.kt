package de.heckenmann.visualagent.config

import de.heckenmann.visualagent.knowledge.PreferenceStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Application configuration singleton.
 *
 * Loads configuration from `src/main/resources/config/app.properties`.
 * Provides access to Ollama settings, database path, UI preferences, and browser configuration.
 *
 * @property llmProvider Active provider identifier (`ollama` or `openai`)
 * @property ollamaLocalUrl Ollama API endpoint (default: http://localhost:11434)
 * @property ollamaModel Default model name (default: llava)
 * @property openAiApiKey Plaintext OpenAI API key stored in SQLite by current product decision
 * @property openAiBaseUrl OpenAI-compatible API base URL
 * @property openAiModel Default OpenAI-compatible chat model
 * @property databasePath Path to SQLite database file
 * @property theme UI theme (default: Dracula)
 * @property fontSize UI font size (default: 14)
 * @property browserDefault Default browser for web integration (default: firefox)
 */
class AppConfig private constructor() {
    var llmProvider: String = "ollama"
    var ollamaLocalUrl: String = "http://localhost:11434"
    var ollamaModel: String = "llava"
    var openAiApiKey: String = ""
    var openAiBaseUrl: String = "https://api.openai.com"
    var openAiModel: String = "gpt-4o-mini"
    var databasePath: String = "./data/visual-agent.db"
    var theme: String = "Dracula"
    var fontSize: Int = 14
    var browserDefault: String = "firefox"
    var contextLength: Int = 4096
    var streamingEnabled: Boolean = true
    var thinkingEnabled: Boolean = false
    var autoCompactionEnabled: Boolean = true
    var loadLimit: Int = 50
    var maxParallelSubAgents: Int = 4
    var timeoutSeconds: Int = 120
    var userModelInstruction: String = ""
    var favoriteModels: String = ""

    private val listeners = CopyOnWriteArrayList<(AppConfigChange) -> Unit>()
    private var lastSnapshot = snapshot()

    @Volatile
    private var preferenceStore: PreferenceStore? = null

    companion object {
        /** Singleton instance of AppConfig. */
        val instance: AppConfig by lazy { AppConfig().reload() }

        private const val KEY_LLM_PROVIDER = "llm.provider"
        private const val KEY_OLLAMA_LOCAL_URL = "ollama.local.url"
        private const val KEY_OLLAMA_MODEL = "ollama.model"
        private const val KEY_OPENAI_API_KEY = "openai.api.key"
        private const val KEY_OPENAI_BASE_URL = "openai.base.url"
        private const val KEY_OPENAI_MODEL = "openai.model"
        private const val KEY_DATABASE_PATH = "database.path"
        private const val KEY_UI_THEME = "ui.theme"
        private const val KEY_UI_FONT_SIZE = "ui.font.size"
        private const val KEY_BROWSER_DEFAULT = "browser.default"
        private const val KEY_SESSION_CONTEXT_LENGTH = "session.context.length"
        private const val KEY_SESSION_STREAMING = "session.streaming.enabled"
        private const val KEY_SESSION_THINKING = "session.thinking.enabled"
        private const val KEY_SESSION_AUTO_COMPACTION = "session.auto.compaction.enabled"
        private const val KEY_SESSION_LOAD_LIMIT = "session.load.limit"
        private const val KEY_SESSION_MAX_PARALLEL_SUB_AGENTS = "session.max.parallel.sub.agents"
        private const val KEY_SESSION_TIMEOUT_SECONDS = "session.timeout.seconds"
        private const val KEY_SESSION_USER_MODEL_INSTRUCTION = "session.user.model.instruction"
        private const val KEY_SESSION_FAVORITE_MODELS = "session.favorite.models"
    }

    /**
     * Registers an observer that receives configuration changes after [save] or [reload].
     *
     * @param listener Callback invoked once per changed key
     * @return Closeable registration that removes the listener when closed
     */
    fun addChangeListener(listener: (AppConfigChange) -> Unit): AutoCloseable {
        listeners.add(listener)
        return AutoCloseable { listeners.remove(listener) }
    }

    /**
     * Binds the Spring-managed persistence facade used for settings storage.
     *
     * @param store Shared preference store
     */
    fun bindPreferenceStore(store: PreferenceStore) {
        preferenceStore = store
        loadFromDatabase()
        publishChanges()
    }

    /**
     * Gets the AtlantaFX theme stylesheet for the current theme setting.
     *
     * @return The fully qualified path to the theme stylesheet, or Dracula as default
     */
    fun getThemeStylesheet(): String = AppThemeStylesheets.stylesheetFor(theme)

    /**
     * Returns the currently selected model for the active provider.
     *
     * @return Active model name used for new provider requests
     */
    fun activeModel(): String =
        when (normalizedProvider()) {
            "openai" -> openAiModel
            else -> ollamaModel
        }

    /**
     * Updates the selected model for the active provider.
     *
     * @param model Model name selected in the session UI
     */
    fun setActiveModel(model: String) {
        when (normalizedProvider()) {
            "openai" -> openAiModel = model
            else -> ollamaModel = model
        }
    }

    /**
     * Returns the persisted provider after normalizing unsupported values to Ollama.
     *
     * @return `ollama` or `openai`
     */
    fun normalizedProvider(): String =
        when (llmProvider.lowercase()) {
            "openai" -> "openai"
            else -> "ollama"
        }

    /**
     * Saves current configuration to app.properties file.
     * Called when user changes settings in the application.
     */
    fun save() {
        saveToProperties()
        saveToDatabase()
        publishChanges()
    }

    /**
     * Reloads settings from persistence.
     *
     * Loads file-backed bootstrap settings first and then overlays values stored in the DB.
     */
    fun reload(): AppConfig {
        loadFromProperties()
        loadFromDatabase()
        publishChanges()
        return this
    }

    private fun publishChanges() {
        val previous = lastSnapshot
        val current = snapshot()
        lastSnapshot = current

        current.values.forEach { (key, value) ->
            val oldValue = previous.values[key]
            if (oldValue != value) {
                val change = AppConfigChange(key = key, oldValue = oldValue, newValue = value)
                listeners.forEach { listener -> listener(change) }
            }
        }
    }

    private fun snapshot(): AppConfigSnapshot =
        AppConfigSnapshot(
            mapOf(
                KEY_LLM_PROVIDER to llmProvider,
                KEY_OLLAMA_LOCAL_URL to ollamaLocalUrl,
                KEY_OLLAMA_MODEL to ollamaModel,
                KEY_OPENAI_API_KEY to openAiApiKey,
                KEY_OPENAI_BASE_URL to openAiBaseUrl,
                KEY_OPENAI_MODEL to openAiModel,
                KEY_DATABASE_PATH to databasePath,
                KEY_UI_THEME to theme,
                KEY_UI_FONT_SIZE to fontSize.toString(),
                KEY_BROWSER_DEFAULT to browserDefault,
                KEY_SESSION_CONTEXT_LENGTH to contextLength.toString(),
                KEY_SESSION_STREAMING to streamingEnabled.toString(),
                KEY_SESSION_THINKING to thinkingEnabled.toString(),
                KEY_SESSION_AUTO_COMPACTION to autoCompactionEnabled.toString(),
                KEY_SESSION_LOAD_LIMIT to loadLimit.toString(),
                KEY_SESSION_MAX_PARALLEL_SUB_AGENTS to maxParallelSubAgents.toString(),
                KEY_SESSION_TIMEOUT_SECONDS to timeoutSeconds.toString(),
                KEY_SESSION_USER_MODEL_INSTRUCTION to userModelInstruction,
                KEY_SESSION_FAVORITE_MODELS to favoriteModels,
            ),
        )

    private fun saveToProperties() {
        val configFile = File("src/main/resources/config/app.properties")
        val props = Properties()
        props.setProperty(KEY_LLM_PROVIDER, llmProvider)
        props.setProperty(KEY_OLLAMA_LOCAL_URL, ollamaLocalUrl)
        props.setProperty(KEY_OLLAMA_MODEL, ollamaModel)
        props.setProperty(KEY_OPENAI_BASE_URL, openAiBaseUrl)
        props.setProperty(KEY_OPENAI_MODEL, openAiModel)
        props.setProperty(KEY_DATABASE_PATH, databasePath)
        props.setProperty(KEY_UI_THEME, theme)
        props.setProperty(KEY_UI_FONT_SIZE, fontSize.toString())
        props.setProperty(KEY_BROWSER_DEFAULT, browserDefault)
        props.setProperty(KEY_SESSION_FAVORITE_MODELS, favoriteModels)
        FileOutputStream(configFile).use { fos ->
            props.store(fos, "Visual Agent Configuration")
        }
    }

    private fun saveToDatabase() {
        runCatching {
            val db = preferenceStore ?: return@runCatching
            db.setPreference(KEY_LLM_PROVIDER, llmProvider)
            db.setPreference(KEY_OLLAMA_LOCAL_URL, ollamaLocalUrl)
            db.setPreference(KEY_OLLAMA_MODEL, ollamaModel)
            db.setPreference(KEY_OPENAI_API_KEY, openAiApiKey)
            db.setPreference(KEY_OPENAI_BASE_URL, openAiBaseUrl)
            db.setPreference(KEY_OPENAI_MODEL, openAiModel)
            db.setPreference(KEY_DATABASE_PATH, databasePath)
            db.setPreference(KEY_UI_THEME, theme)
            db.setPreference(KEY_UI_FONT_SIZE, fontSize.toString())
            db.setPreference(KEY_BROWSER_DEFAULT, browserDefault)
            db.setPreference(KEY_SESSION_CONTEXT_LENGTH, contextLength.toString())
            db.setPreference(KEY_SESSION_STREAMING, streamingEnabled.toString())
            db.setPreference(KEY_SESSION_THINKING, thinkingEnabled.toString())
            db.setPreference(KEY_SESSION_AUTO_COMPACTION, autoCompactionEnabled.toString())
            db.setPreference(KEY_SESSION_LOAD_LIMIT, loadLimit.toString())
            db.setPreference(KEY_SESSION_MAX_PARALLEL_SUB_AGENTS, maxParallelSubAgents.toString())
            db.setPreference(KEY_SESSION_TIMEOUT_SECONDS, timeoutSeconds.toString())
            db.setPreference(KEY_SESSION_USER_MODEL_INSTRUCTION, userModelInstruction)
            db.setPreference(KEY_SESSION_FAVORITE_MODELS, favoriteModels)
        }
    }

    private fun loadFromProperties() {
        val configFile = File("src/main/resources/config/app.properties")

        if (configFile.exists()) {
            val props = Properties()
            FileInputStream(configFile).use { fis ->
                props.load(fis)
            }

            llmProvider = props.getProperty(KEY_LLM_PROVIDER, llmProvider)
            ollamaLocalUrl = props.getProperty(KEY_OLLAMA_LOCAL_URL, ollamaLocalUrl)
            ollamaModel = props.getProperty(KEY_OLLAMA_MODEL, ollamaModel)
            openAiBaseUrl = props.getProperty(KEY_OPENAI_BASE_URL, openAiBaseUrl)
            openAiModel = props.getProperty(KEY_OPENAI_MODEL, openAiModel)
            databasePath = props.getProperty(KEY_DATABASE_PATH, databasePath)
            theme = props.getProperty(KEY_UI_THEME, theme)
            fontSize = props.getProperty(KEY_UI_FONT_SIZE, fontSize.toString()).toIntOrNull() ?: fontSize
            browserDefault = props.getProperty(KEY_BROWSER_DEFAULT, browserDefault)
            favoriteModels = props.getProperty(KEY_SESSION_FAVORITE_MODELS, favoriteModels)
        }
    }

    private fun loadFromDatabase() {
        runCatching {
            val db = preferenceStore ?: return@runCatching
            llmProvider = db.getPreference(KEY_LLM_PROVIDER) ?: llmProvider
            ollamaLocalUrl = db.getPreference(KEY_OLLAMA_LOCAL_URL) ?: ollamaLocalUrl
            ollamaModel = db.getPreference(KEY_OLLAMA_MODEL) ?: ollamaModel
            openAiApiKey = db.getPreference(KEY_OPENAI_API_KEY) ?: openAiApiKey
            openAiBaseUrl = db.getPreference(KEY_OPENAI_BASE_URL) ?: openAiBaseUrl
            openAiModel = db.getPreference(KEY_OPENAI_MODEL) ?: openAiModel
            theme = db.getPreference(KEY_UI_THEME) ?: theme
            fontSize = db.getPreference(KEY_UI_FONT_SIZE)?.toIntOrNull() ?: fontSize
            browserDefault = db.getPreference(KEY_BROWSER_DEFAULT) ?: browserDefault
            contextLength = db.getPreference(KEY_SESSION_CONTEXT_LENGTH)?.toIntOrNull() ?: contextLength
            streamingEnabled = db.getPreference(KEY_SESSION_STREAMING)?.toBooleanStrictOrNull() ?: streamingEnabled
            thinkingEnabled = db.getPreference(KEY_SESSION_THINKING)?.toBooleanStrictOrNull() ?: thinkingEnabled
            autoCompactionEnabled = db.getPreference(KEY_SESSION_AUTO_COMPACTION)?.toBooleanStrictOrNull() ?: autoCompactionEnabled
            loadLimit = db.getPreference(KEY_SESSION_LOAD_LIMIT)?.toIntOrNull() ?: loadLimit
            maxParallelSubAgents = db.getPreference(KEY_SESSION_MAX_PARALLEL_SUB_AGENTS)?.toIntOrNull() ?: maxParallelSubAgents
            timeoutSeconds = db.getPreference(KEY_SESSION_TIMEOUT_SECONDS)?.toIntOrNull() ?: timeoutSeconds
            userModelInstruction = db.getPreference(KEY_SESSION_USER_MODEL_INSTRUCTION) ?: userModelInstruction
            favoriteModels = db.getPreference(KEY_SESSION_FAVORITE_MODELS) ?: favoriteModels
        }
    }
}
