package de.heckenmann.visualagent.ui.settings

import de.heckenmann.visualagent.protocol.CapabilitySupport
import de.heckenmann.visualagent.protocol.capabilitySupport

/** Allows saving only a valid selection, preserving unchanged catalogs with missing discovery metadata. */
internal fun ProviderSettingsDraft.canSaveModelSelection(configurationChanged: Boolean): Boolean =
    modelSelectionError() == null &&
        providerId.isNotBlank() &&
        modelId.isNotBlank() &&
        (!configurationChanged || providers.firstOrNull { it.id == providerId }?.selectableModels()?.any { it.id == modelId } == true)

/** Separates catalog inspection from activating a model that explicitly excludes chat. */
internal fun ProviderSettingsDraft.modelSelectionError(): String? {
    val active = providers.firstOrNull { it.id == providerId }?.models?.firstOrNull { it.id == modelId }
    if (active?.capabilitySupport("chat") == CapabilitySupport.UNSUPPORTED) {
        return "The active model declares CHAT unsupported. Correct its capability declaration before saving."
    }
    val settings = conversationSettings
    if (settings.promptInterpolationProviderId.isBlank()) return null
    val interpolation =
        providers
            .firstOrNull { it.id == settings.promptInterpolationProviderId && it.enabled }
            ?.selectableModels()
            ?.firstOrNull { it.id == settings.promptInterpolationModelId }
            ?: return "The interpolation model is unavailable. Choose an enabled chat-capable model."
    return if (interpolation.capabilitySupport("chat") == CapabilitySupport.UNSUPPORTED) {
        "The interpolation model declares CHAT unsupported. Correct its capability declaration before saving."
    } else {
        null
    }
}
