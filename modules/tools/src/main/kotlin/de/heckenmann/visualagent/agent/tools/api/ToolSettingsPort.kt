package de.heckenmann.visualagent.agent.tools.api

/** Safe UI and active-model settings exposed to tools. */
interface ToolSettingsPort {
    /** Reads the current safe settings snapshot. */
    fun read(): ToolSettings

    /** Applies provided setting values and persists them. */
    fun update(update: ToolSettingsUpdate): ToolSettings
}

/** Safe settings snapshot; secret values are represented only by presence flags. */
data class ToolSettings(
    val fontSize: Int,
    val provider: String,
    val model: String,
    val openAiBaseUrl: String,
    val openAiApiKeyConfigured: Boolean,
    val streamingEnabled: Boolean,
    val thinkingEnabled: Boolean,
    val timeoutSeconds: Int,
    val uiScalePercent: Int?,
)

/** Optional settings supplied by the UI tool. */
data class ToolSettingsUpdate(
    val fontSize: Int? = null,
    val provider: String? = null,
    val model: String? = null,
    val openAiBaseUrl: String? = null,
    val streamingEnabled: Boolean? = null,
    val thinkingEnabled: Boolean? = null,
    val uiScalePercent: Int? = null,
)
