package de.heckenmann.visualagent.agent.provider

/** Merges discovered model metadata without losing user-configured model settings. */
internal fun List<ProviderModelConfig>.mergeWithExisting(existing: Map<String, ProviderModelConfig>): List<ProviderModelConfig> =
    distinctBy(ProviderModelConfig::id).map { discovered ->
        val configured = existing[discovered.id]
        configured?.copy(
            name = discovered.name,
            contextLimit = discovered.contextLimit ?: configured.contextLimit,
            outputLimit = discovered.outputLimit ?: configured.outputLimit,
            capabilities =
                when {
                    configured.capabilitiesOverridden -> configured.capabilities
                    discovered.capabilitiesComplete || discovered.capabilities.isNotEmpty() -> discovered.capabilities
                    else -> configured.capabilities
                },
            capabilitiesComplete =
                when {
                    configured.capabilitiesOverridden -> configured.capabilitiesComplete
                    discovered.capabilitiesComplete || discovered.capabilities.isNotEmpty() -> discovered.capabilitiesComplete
                    else -> configured.capabilitiesComplete
                },
        ) ?: discovered
    }
