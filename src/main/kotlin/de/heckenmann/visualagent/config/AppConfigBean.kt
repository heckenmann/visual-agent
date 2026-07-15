package de.heckenmann.visualagent.config

import de.heckenmann.visualagent.knowledge.PreferenceStore
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Spring-managed settings bean that holds the resolved application configuration.
 *
 * [AppConfigPersistenceBinder] populates this bean from the database-backed
 * preference store after Spring wiring is complete. Components inject this bean
 * instead of reading [AppConfig.instance] directly.
 */
@Component
class AppConfigBean(
    private val preferenceStore: PreferenceStore = NoOpPreferenceStore(),
) {
    var llmProvider: String = "ollama"
    var ollamaLocalUrl: String = "http://localhost:11434"
    var ollamaModel: String = "llava"
    var ollamaApiKey: String = ""
    var openAiApiKey: String = ""
    var openAiBaseUrl: String = "https://api.openai.com"
    var openAiModel: String = "gpt-4o-mini"
    var databasePath: String = "./data/visual-agent.db"
    var uiThemeMode: ThemeMode = ThemeMode.SYSTEM
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

    /**
     * Returns the currently selected model for the active provider.
     */
    fun activeModel(): String =
        when (normalizedProvider()) {
            "openai" -> openAiModel
            else -> ollamaModel
        }

    /**
     * Updates the selected model for the active provider.
     */
    fun setActiveModel(model: String) {
        when (normalizedProvider()) {
            "openai" -> openAiModel = model
            else -> ollamaModel = model
        }
    }

    /**
     * Returns the persisted provider after normalizing unsupported values to Ollama.
     */
    fun normalizedProvider(): String =
        when (llmProvider.lowercase()) {
            "openai" -> "openai"
            else -> "ollama"
        }

    private val listeners = CopyOnWriteArrayList<(AppConfigChange) -> Unit>()

    /**
     * Registers an observer that receives configuration changes after [save].
     */
    fun addChangeListener(listener: (AppConfigChange) -> Unit): AutoCloseable {
        listeners.add(listener)
        return AutoCloseable { listeners.remove(listener) }
    }

    /**
     * Persists current settings to the database-backed preference store.
     */
    fun save() {
        preferenceStore.setPreference(KEY_LLM_PROVIDER, llmProvider)
        preferenceStore.setPreference(KEY_OLLAMA_LOCAL_URL, ollamaLocalUrl)
        preferenceStore.setPreference(KEY_OLLAMA_MODEL, ollamaModel)
        preferenceStore.setPreference(KEY_OLLAMA_API_KEY, ollamaApiKey)
        preferenceStore.setPreference(KEY_OPENAI_API_KEY, openAiApiKey)
        preferenceStore.setPreference(KEY_OPENAI_BASE_URL, openAiBaseUrl)
        preferenceStore.setPreference(KEY_OPENAI_MODEL, openAiModel)
        preferenceStore.setPreference(KEY_UI_THEME_MODE, uiThemeMode.name)
        preferenceStore.setPreference(KEY_UI_FONT_SIZE, fontSize.toString())
        preferenceStore.setPreference(KEY_BROWSER_DEFAULT, browserDefault)
        preferenceStore.setPreference(KEY_SESSION_CONTEXT_LENGTH, contextLength.toString())
        preferenceStore.setPreference(KEY_SESSION_STREAMING, streamingEnabled.toString())
        preferenceStore.setPreference(KEY_SESSION_THINKING, thinkingEnabled.toString())
        preferenceStore.setPreference(KEY_SESSION_AUTO_COMPACTION, autoCompactionEnabled.toString())
        preferenceStore.setPreference(KEY_SESSION_LOAD_LIMIT, loadLimit.toString())
        preferenceStore.setPreference(KEY_SESSION_MAX_PARALLEL_SUB_AGENTS, maxParallelSubAgents.toString())
        preferenceStore.setPreference(KEY_SESSION_TIMEOUT_SECONDS, timeoutSeconds.toString())
        preferenceStore.setPreference(KEY_SESSION_USER_MODEL_INSTRUCTION, userModelInstruction)
        preferenceStore.setPreference(KEY_SESSION_FAVORITE_MODELS, favoriteModels)
        publishChanges()
    }

    private fun publishChanges() {
        val snapshot = snapshot()
        snapshot.forEach { (key, value) ->
            listeners.forEach { listener -> listener(AppConfigChange(key = key, oldValue = null, newValue = value)) }
        }
    }

    private fun snapshot(): Map<String, String> =
        mapOf(
            KEY_LLM_PROVIDER to llmProvider,
            KEY_OLLAMA_LOCAL_URL to ollamaLocalUrl,
            KEY_OLLAMA_MODEL to ollamaModel,
            KEY_OLLAMA_API_KEY to ollamaApiKey,
            KEY_OPENAI_API_KEY to openAiApiKey,
            KEY_OPENAI_BASE_URL to openAiBaseUrl,
            KEY_OPENAI_MODEL to openAiModel,
            KEY_UI_THEME_MODE to uiThemeMode.name,
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
        )

    companion object {
        internal const val KEY_LLM_PROVIDER = "llm.provider"
        internal const val KEY_OLLAMA_LOCAL_URL = "ollama.local.url"
        internal const val KEY_OLLAMA_MODEL = "ollama.model"
        internal const val KEY_OLLAMA_API_KEY = "ollama.api.key"
        internal const val KEY_OPENAI_API_KEY = "openai.api.key"
        internal const val KEY_OPENAI_BASE_URL = "openai.base.url"
        internal const val KEY_OPENAI_MODEL = "openai.model"
        internal const val KEY_DATABASE_PATH = "database.path"
        internal const val KEY_UI_THEME_MODE = "ui.theme.mode"
        internal const val KEY_UI_FONT_SIZE = "ui.font.size"
        internal const val KEY_BROWSER_DEFAULT = "browser.default"
        internal const val KEY_SESSION_CONTEXT_LENGTH = "session.context.length"
        internal const val KEY_SESSION_STREAMING = "session.streaming.enabled"
        internal const val KEY_SESSION_THINKING = "session.thinking.enabled"
        internal const val KEY_SESSION_AUTO_COMPACTION = "session.auto.compaction.enabled"
        internal const val KEY_SESSION_LOAD_LIMIT = "session.load.limit"
        internal const val KEY_SESSION_MAX_PARALLEL_SUB_AGENTS = "session.max.parallel.sub.agents"
        internal const val KEY_SESSION_TIMEOUT_SECONDS = "session.timeout.seconds"
        internal const val KEY_SESSION_USER_MODEL_INSTRUCTION = "session.user.model.instruction"
        internal const val KEY_SESSION_FAVORITE_MODELS = "session.favorite.models"
    }
}

/**
 * No-op preference store used as default when no database is available.
 */
internal class NoOpPreferenceStore : PreferenceStore {
    private val map = mutableMapOf<String, String>()

    override fun getPreference(key: String): String? = map[key]

    override fun setPreference(
        key: String,
        value: String,
    ) {
        map[key] = value
    }
}
