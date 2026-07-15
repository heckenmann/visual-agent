package de.heckenmann.visualagent.config

import de.heckenmann.visualagent.knowledge.PreferenceStore
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

/**
 * Populates the [AppConfigBean] from the database-backed preference store.
 */
@Component
class AppConfigPersistenceBinder(
    private val preferenceStore: PreferenceStore,
    private val appConfigBean: AppConfigBean,
) {
    /**
     * Loads all user preferences from the database into the [AppConfigBean].
     */
    @PostConstruct
    fun bind() {
        appConfigBean.llmProvider = preferenceStore.getPreference(AppConfigBean.KEY_LLM_PROVIDER) ?: appConfigBean.llmProvider
        appConfigBean.ollamaLocalUrl = preferenceStore.getPreference(AppConfigBean.KEY_OLLAMA_LOCAL_URL) ?: appConfigBean.ollamaLocalUrl
        appConfigBean.ollamaModel = preferenceStore.getPreference(AppConfigBean.KEY_OLLAMA_MODEL) ?: appConfigBean.ollamaModel
        appConfigBean.ollamaApiKey = preferenceStore.getPreference(AppConfigBean.KEY_OLLAMA_API_KEY) ?: appConfigBean.ollamaApiKey
        appConfigBean.openAiApiKey = preferenceStore.getPreference(AppConfigBean.KEY_OPENAI_API_KEY) ?: appConfigBean.openAiApiKey
        appConfigBean.openAiBaseUrl = preferenceStore.getPreference(AppConfigBean.KEY_OPENAI_BASE_URL) ?: appConfigBean.openAiBaseUrl
        appConfigBean.openAiModel = preferenceStore.getPreference(AppConfigBean.KEY_OPENAI_MODEL) ?: appConfigBean.openAiModel
        appConfigBean.uiThemeMode =
            preferenceStore.getPreference(AppConfigBean.KEY_UI_THEME_MODE)?.let(ThemeMode::fromString) ?: appConfigBean.uiThemeMode
        appConfigBean.fontSize = preferenceStore.getPreference(AppConfigBean.KEY_UI_FONT_SIZE)?.toIntOrNull() ?: appConfigBean.fontSize
        appConfigBean.browserDefault = preferenceStore.getPreference(AppConfigBean.KEY_BROWSER_DEFAULT) ?: appConfigBean.browserDefault
        appConfigBean.contextLength =
            preferenceStore.getPreference(AppConfigBean.KEY_SESSION_CONTEXT_LENGTH)?.toIntOrNull() ?: appConfigBean.contextLength
        appConfigBean.streamingEnabled =
            preferenceStore.getPreference(AppConfigBean.KEY_SESSION_STREAMING)?.toBooleanStrictOrNull() ?: appConfigBean.streamingEnabled
        appConfigBean.thinkingEnabled =
            preferenceStore.getPreference(AppConfigBean.KEY_SESSION_THINKING)?.toBooleanStrictOrNull() ?: appConfigBean.thinkingEnabled
        appConfigBean.autoCompactionEnabled =
            preferenceStore.getPreference(AppConfigBean.KEY_SESSION_AUTO_COMPACTION)?.toBooleanStrictOrNull()
                ?: appConfigBean.autoCompactionEnabled
        appConfigBean.loadLimit =
            preferenceStore.getPreference(AppConfigBean.KEY_SESSION_LOAD_LIMIT)?.toIntOrNull() ?: appConfigBean.loadLimit
        appConfigBean.maxParallelSubAgents =
            preferenceStore.getPreference(AppConfigBean.KEY_SESSION_MAX_PARALLEL_SUB_AGENTS)?.toIntOrNull()
                ?: appConfigBean.maxParallelSubAgents
        appConfigBean.timeoutSeconds =
            preferenceStore.getPreference(AppConfigBean.KEY_SESSION_TIMEOUT_SECONDS)?.toIntOrNull() ?: appConfigBean.timeoutSeconds
        appConfigBean.userModelInstruction =
            preferenceStore.getPreference(AppConfigBean.KEY_SESSION_USER_MODEL_INSTRUCTION) ?: appConfigBean.userModelInstruction
        appConfigBean.favoriteModels =
            preferenceStore.getPreference(AppConfigBean.KEY_SESSION_FAVORITE_MODELS) ?: appConfigBean.favoriteModels
    }
}
