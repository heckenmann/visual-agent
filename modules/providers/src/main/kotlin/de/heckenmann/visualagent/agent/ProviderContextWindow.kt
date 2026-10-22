package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.provider.ProviderProfile

/** Applies provider-reported limits while preserving the caller's configured context policy. */
internal fun ProviderProfile.contextWindow(
    modelId: String,
    current: ContextWindow,
): ContextWindow {
    val modelConfig = models.firstOrNull { it.id == modelId }
    return current.copy(modelLimit = modelConfig?.contextLimit, outputLimit = modelConfig?.outputLimit)
}
