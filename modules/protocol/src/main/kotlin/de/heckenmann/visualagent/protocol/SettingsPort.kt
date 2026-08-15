package de.heckenmann.visualagent.protocol

/** Runtime and presentation settings exchanged with the application. */
interface SettingsPort {
    /** Reads the current settings snapshot. */
    fun snapshot(): SettingsSnapshot

    /** Persists the supplied settings snapshot. */
    fun save(settings: SettingsSnapshot)

    /** Registers a listener for settings changes. */
    fun addChangeListener(listener: (SettingsSnapshot) -> Unit): AutoCloseable
}

/** Serializable settings state needed by the Compose shell and panels. */
data class SettingsSnapshot(
    val providerId: String = "ollama",
    val modelId: String = "",
    val uiThemeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontSize: Int = 14,
    val showPanelLabels: Boolean = true,
    val contextLength: Int = 4096,
    val streamingEnabled: Boolean = true,
    val thinkingEnabled: Boolean = false,
    val autoCompactionEnabled: Boolean = true,
    val loadLimit: Int = 50,
    val maxParallelSubAgents: Int = 4,
    val timeoutSeconds: Int = 120,
    val userModelInstruction: String = "",
    val favoriteModels: List<String> = emptyList(),
    val queueFlushMode: String = "ONE_BY_ONE",
)

/** Theme selection exposed to the presentation layer. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}
