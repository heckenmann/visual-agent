package de.heckenmann.visualagent.ui.compose

import de.heckenmann.visualagent.config.AppConfigBean

/**
 * Applies settings values to the config bean and persists them.
 */
internal fun applyAndSaveSettings(
    config: AppConfigBean,
    fontSize: Int,
    contextLength: Int,
    loadLimitValue: Int?,
    maxParallelValue: Int?,
    timeoutValue: Int?,
    streamingEnabled: Boolean,
    thinkingEnabled: Boolean,
    autoCompactionEnabled: Boolean,
    queueFlushMode: String,
    userInstruction: String,
) {
    config.fontSize = fontSize.clampFontSize()
    config.contextLength = contextLength.coerceIn(MIN_CONTEXT_LENGTH, MAX_CONTEXT_LENGTH)
    config.loadLimit = loadLimitValue ?: config.loadLimit
    config.maxParallelSubAgents = maxParallelValue ?: config.maxParallelSubAgents
    config.timeoutSeconds = timeoutValue ?: config.timeoutSeconds
    config.streamingEnabled = streamingEnabled
    config.thinkingEnabled = thinkingEnabled
    config.autoCompactionEnabled = autoCompactionEnabled
    config.queueFlushMode = queueFlushMode
    config.userModelInstruction = userInstruction
    config.save()
}
