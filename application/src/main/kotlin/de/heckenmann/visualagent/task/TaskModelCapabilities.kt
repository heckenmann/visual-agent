package de.heckenmann.visualagent.task

import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.provider.ModelStatus
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

/** Checks current model availability without changing explicit agent selections. */
@Service
class TaskModelCapabilities(
    private val catalog: ObjectProvider<ProviderCatalogService>,
) {
    /** Unknown declarations remain compatible; authoritative tool-less models are rejected. */
    fun supportsTools(agent: SubAgent): Boolean =
        runCatching {
            val providers = catalog.getObject()
            val selection = agent.config.modelSelection()
            val providerId = selection.provider ?: providers.activeProviderId()
            val provider = providers.getProvider(providerId) ?: return@runCatching false
            if (!provider.enabled) return@runCatching false
            val modelId =
                selection.model
                    ?: if (selection.provider == null) providers.activeModelId() else provider.defaultModel
            if (modelId.isBlank()) return@runCatching false
            if (modelId in provider.modelBlacklist ||
                (provider.modelWhitelist.isNotEmpty() && modelId !in provider.modelWhitelist)
            ) {
                return@runCatching false
            }
            val model = provider.models.firstOrNull { it.id == modelId }
            if (model?.status in setOf(ModelStatus.DISABLED, ModelStatus.DEPRECATED)) return@runCatching false
            model == null || !model.capabilitiesComplete || model.capabilities.any { it.equals("tools", ignoreCase = true) }
        }.getOrDefault(false)
}
