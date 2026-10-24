package de.heckenmann.visualagent.protocol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Runtime and presentation settings exchanged with the application. */
interface SettingsPort {
    /** Reads the current settings snapshot. */
    fun snapshot(): SettingsSnapshot

    /** Reads the current settings snapshot without blocking the presentation dispatcher. */
    suspend fun snapshotAsync(): SettingsSnapshot = withContext(Dispatchers.IO) { snapshot() }

    /**
     * Persists the supplied settings snapshot and, when provided, the complete provider
     * configuration as one application-level change.
     *
     * @param settings Runtime and presentation settings to persist
     * @param providerConfiguration Optional staged provider and model configuration
     */
    fun save(
        settings: SettingsSnapshot,
        providerConfiguration: ProviderConfiguration? = null,
    )

    /** Persists only the fields owned by one settings section, preserving other concurrent edits. */
    fun saveSection(
        section: SettingsSection,
        settings: SettingsSnapshot,
    ): Unit = throw UnsupportedOperationException("Section-scoped settings updates are unavailable")

    /** Changes only the main-agent selection and adopts a reported positive model context limit. */
    suspend fun selectConversationModel(
        providerId: String,
        modelId: String,
    ): Unit = throw UnsupportedOperationException("Conversation model selection is unavailable")

    /** Updates one model favorite without submitting any other settings fields. */
    fun setModelFavorite(
        modelId: String,
        favorite: Boolean,
    ): Unit = throw UnsupportedOperationException("Model favorites are unavailable")

    /** Registers a listener for settings changes. */
    fun addChangeListener(listener: (SettingsSnapshot) -> Unit): AutoCloseable
}

/** Ownership boundaries for explicit, independently saved settings drafts. */
enum class SettingsSection {
    APPEARANCE,
    CONVERSATION,
    AGENT,
    MODEL,
    UPDATES,
}

/** Serializable settings state needed by the Compose shell and panels. */
data class SettingsSnapshot(
    val providerId: String = "ollama",
    val modelId: String = "",
    val uiThemeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontSize: Int = 14,
    val uiScalePercent: Int? = null,
    val showPanelLabels: Boolean = true,
    val contextLength: Int = 4096,
    val loadLimit: Int = 50,
    val maxParallelSubAgents: Int = 4,
    val timeoutSeconds: Int = 120,
    val userModelInstruction: String = "",
    val favoriteModels: List<String> = emptyList(),
    val queueFlushMode: String = "ONE_BY_ONE",
    val maxMainAgentMemoryChars: Int = 12_000,
    val promptInterpolationProviderId: String = "",
    val promptInterpolationModelId: String = "",
    val automaticPromptInterpolation: Boolean = false,
    val promptInterpolationContextMessages: Int = 8,
    val followUpSuggestionsEnabled: Boolean = true,
    val followUpSuggestionIdleDelaySeconds: Int = 3,
    val followUpSuggestionCount: Int = 3,
    val automaticUpdatesEnabled: Boolean = true,
    val includePrereleaseUpdates: Boolean = false,
)

/**
 * A complete staged provider catalog together with the main agent selection.
 *
 * The UI keeps this value local until the user explicitly saves the surrounding settings
 * dialog, so editing credentials or selecting another model cannot affect an active request.
 */
data class ProviderConfiguration(
    val providers: List<ProviderProfile>,
    val providerId: String,
    val modelId: String,
)

/** Theme selection exposed to the presentation layer. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}
