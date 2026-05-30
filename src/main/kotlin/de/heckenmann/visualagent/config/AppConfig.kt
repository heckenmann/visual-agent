package de.heckenmann.visualagent.config

import de.heckenmann.visualagent.knowledge.KnowledgeDb
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
 * @property ollamaLocalUrl Ollama API endpoint (default: http://localhost:11434)
 * @property ollamaModel Default model name (default: llava)
 * @property databasePath Path to SQLite database file
 * @property theme UI theme (default: Dracula)
 * @property fontSize UI font size (default: 14)
 * @property browserDefault Default browser for web integration (default: firefox)
 */
class AppConfig private constructor() {

    var ollamaLocalUrl: String = "http://localhost:11434"
    // Default to a more commonly available model name; can be overridden in app.properties
    var ollamaModel: String = "llava"
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

    private val listeners = CopyOnWriteArrayList<(AppConfigChange) -> Unit>()
    private var lastSnapshot = snapshot()

    companion object {
        /** Singleton instance of AppConfig. */
        val instance: AppConfig by lazy { AppConfig().reload() }

        private const val KEY_OLLAMA_LOCAL_URL = "ollama.local.url"
        private const val KEY_OLLAMA_MODEL = "ollama.model"
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
     * Gets the AtlantaFX theme stylesheet for the current theme setting.
     *
     * @return The fully qualified path to the theme stylesheet, or Dracula as default
     */
    fun getThemeStylesheet(): String {
        return when (theme) {
            "Dracula" -> atlantafx.base.theme.Dracula().getUserAgentStylesheet()
            "Primer Dark" -> atlantafx.base.theme.PrimerDark().getUserAgentStylesheet()
            "Primer Light" -> atlantafx.base.theme.PrimerLight().getUserAgentStylesheet()
            "Nord Dark" -> atlantafx.base.theme.NordDark().getUserAgentStylesheet()
            "Nord Light" -> atlantafx.base.theme.NordLight().getUserAgentStylesheet()
            "Cupertino Dark" -> atlantafx.base.theme.CupertinoDark().getUserAgentStylesheet()
            "Cupertino Light" -> atlantafx.base.theme.CupertinoLight().getUserAgentStylesheet()
            else -> atlantafx.base.theme.Dracula().getUserAgentStylesheet()
        }
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
                KEY_OLLAMA_LOCAL_URL to ollamaLocalUrl,
                KEY_OLLAMA_MODEL to ollamaModel,
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
            ),
        )

    private fun saveToProperties() {
        val configFile = File("src/main/resources/config/app.properties")
        val props = Properties()
        props.setProperty(KEY_OLLAMA_LOCAL_URL, ollamaLocalUrl)
        props.setProperty(KEY_OLLAMA_MODEL, ollamaModel)
        props.setProperty(KEY_DATABASE_PATH, databasePath)
        props.setProperty(KEY_UI_THEME, theme)
        props.setProperty(KEY_UI_FONT_SIZE, fontSize.toString())
        props.setProperty(KEY_BROWSER_DEFAULT, browserDefault)
        FileOutputStream(configFile).use { fos ->
            props.store(fos, "Visual Agent Configuration")
        }
    }

    private fun saveToDatabase() {
        runCatching {
            val db = KnowledgeDb(databasePath)
            db.setPreference(KEY_OLLAMA_LOCAL_URL, ollamaLocalUrl)
            db.setPreference(KEY_OLLAMA_MODEL, ollamaModel)
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
        }
    }

    private fun loadFromProperties() {
        val configFile = File("src/main/resources/config/app.properties")

        if (configFile.exists()) {
            val props = Properties()
            FileInputStream(configFile).use { fis ->
                props.load(fis)
            }

            ollamaLocalUrl = props.getProperty(KEY_OLLAMA_LOCAL_URL, ollamaLocalUrl)
            ollamaModel = props.getProperty(KEY_OLLAMA_MODEL, ollamaModel)
            databasePath = props.getProperty(KEY_DATABASE_PATH, databasePath)
            theme = props.getProperty(KEY_UI_THEME, theme)
            fontSize = props.getProperty(KEY_UI_FONT_SIZE, fontSize.toString()).toIntOrNull() ?: fontSize
            browserDefault = props.getProperty(KEY_BROWSER_DEFAULT, browserDefault)
        }
    }

    private fun loadFromDatabase() {
        runCatching {
            val db = KnowledgeDb(databasePath)
            ollamaLocalUrl = db.getPreference(KEY_OLLAMA_LOCAL_URL) ?: ollamaLocalUrl
            ollamaModel = db.getPreference(KEY_OLLAMA_MODEL) ?: ollamaModel
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
        }
    }
}

/**
 * Immutable notification payload emitted when one AppConfig value changes.
 *
 * @property key Stable persisted preference key
 * @property oldValue Previous string value, or null when no value was known
 * @property newValue Current string value
 */
data class AppConfigChange(
    val key: String,
    val oldValue: String?,
    val newValue: String,
)

private data class AppConfigSnapshot(
    val values: Map<String, String>,
)
