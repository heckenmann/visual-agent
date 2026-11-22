package de.heckenmann.visualagent.agent.provider

import de.heckenmann.visualagent.model.capabilityKey

/** Merges discovered model metadata without losing user-configured model settings. */
fun List<ProviderModelConfig>.mergeWithExisting(existing: Map<String, ProviderModelConfig>): List<ProviderModelConfig> =
    distinctBy(ProviderModelConfig::id).map { discovered ->
        val configured = existing[discovered.id]
        configured?.copy(
            name = discovered.name,
            contextLimit = discovered.contextLimit ?: configured.contextLimit,
            outputLimit = discovered.outputLimit ?: configured.outputLimit,
            capabilities =
                when {
                    configured.capabilitiesOverridden -> configured.capabilities
                    discovered.capabilitiesComplete -> discovered.capabilities
                    discovered.refreshedCapabilities.isNotEmpty() ->
                        configured.capabilities
                            .filterNot { capability ->
                                capabilityKey(capability) in discovered.refreshedCapabilities
                            }.toSet() +
                            if (configured.capabilitiesComplete) {
                                discovered.capabilities.intersect(discovered.refreshedCapabilities)
                            } else {
                                discovered.capabilities
                            }
                    configured.capabilitiesComplete -> configured.capabilities
                    else -> configured.capabilities + discovered.capabilities
                },
            capabilitiesComplete = configured.capabilitiesComplete || discovered.capabilitiesComplete,
        ) ?: discovered
    }
