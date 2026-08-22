package de.heckenmann.visualagent.agent.provider

/** Merges discovered model metadata without losing user-configured model settings. */
internal fun List<ProviderModelConfig>.mergeWithExisting(existing: Map<String, ProviderModelConfig>): List<ProviderModelConfig> =
    distinctBy(ProviderModelConfig::id).map { discovered ->
        val configured = existing[discovered.id]
        configured?.copy(
            name = discovered.name,
            capabilities = discovered.capabilities.ifEmpty { configured.capabilities },
        ) ?: discovered
    }
