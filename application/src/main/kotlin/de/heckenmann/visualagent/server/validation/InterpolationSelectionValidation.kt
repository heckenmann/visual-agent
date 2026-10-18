package de.heckenmann.visualagent.server.validation

import de.heckenmann.visualagent.agent.provider.ModelCapability
import de.heckenmann.visualagent.agent.provider.ModelStatus
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.provider.supportsCapability

/** Rejects an explicitly configured interpolation model before any settings are persisted. */
fun validateInterpolationSelection(
    profiles: List<ProviderProfile>,
    providerId: String,
    modelId: String,
) {
    val profile = profiles.firstOrNull { it.id == providerId && it.enabled }
    val model = profile?.models?.firstOrNull { it.id == modelId }
    require(
        profile != null &&
            model != null &&
            model.status !in setOf(ModelStatus.DISABLED, ModelStatus.DEPRECATED) &&
            modelId !in profile.modelBlacklist &&
            (profile.modelWhitelist.isEmpty() || modelId in profile.modelWhitelist),
    ) {
        "The interpolation model is unavailable; choose an enabled model in Settings."
    }
    require(model.supportsCapability(ModelCapability.CHAT)) {
        "The interpolation model declares CHAT unsupported. Correct its capability declaration or choose a chat-capable model."
    }
}
