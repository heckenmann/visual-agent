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

    /** Registers a listener for settings changes. */
    fun addChangeListener(listener: (SettingsSnapshot) -> Unit): AutoCloseable
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
