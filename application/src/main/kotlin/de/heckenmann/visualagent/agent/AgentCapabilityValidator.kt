package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.provider.ModelCapability
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.supportsCapability
import org.springframework.stereotype.Component

/** Checks that enabled specialized agent features resolve to usable, declared provider models. */
@Component
class AgentCapabilityValidator(
    private val catalog: ProviderCatalogService,
) {
    /** Validates an assignment before updating an agent, including provider-qualified model IDs. */
    fun validate(config: AgentConfig) {
        require(ModelCapability.CHAT in config.enabledCapabilities) { "CHAT capability cannot be disabled" }
        require(config.capabilityModels.keys.all { it in config.enabledCapabilities }) {
            "Assigned model requires an enabled capability"
        }
        val capabilitiesToValidate =
            config.enabledCapabilities - ModelCapability.CHAT +
                setOfNotNull(ModelCapability.CHAT.takeIf { it in config.capabilityModels })
        for (capability in capabilitiesToValidate) {
            val selection = config.modelSelection(capability)
            val resolved = catalog.resolve(selection.provider, selection.model)
            require(resolved.model.supportsCapability(capability)) {
                "Model '${resolved.provider.id}/${resolved.model.id}' does not support $capability. " +
                    "Pick a $capability-capable model from the provider catalog."
            }
        }
    }
}
