package de.heckenmann.visualagent.agent.provider

/** Assigns the first selectable model with each supported application task capability when unset. */
internal fun CatalogState.withAutomaticTaskModelAssignments(): CatalogState {
    val assignments = taskModelAssignments.toMutableMap()
    val vision = assignments[ModelCapability.VISION.name]
    if (vision != null && !vision.confirmed && vision.providerId != activeProviderId) {
        assignments.remove(ModelCapability.VISION.name)
    }
    val capabilities = ModelCapability.entries - setOf(ModelCapability.CHAT, ModelCapability.TOOLS)
    capabilities.forEach { capability ->
        if (capability.name !in assignments) {
            val profile =
                providers
                    .asSequence()
                    .filter(ProviderProfile::enabled)
                    .filter { capability != ModelCapability.VISION || it.id == activeProviderId }
                    .filter { it.adapter.supportsTaskModel(capability) }
                    .sortedWith(compareBy<ProviderProfile> { if (it.id == activeProviderId) 0 else 1 }.thenBy(ProviderProfile::id))
                    .firstOrNull { candidate ->
                        candidate
                            .selectableModels()
                            .any { it.supportsCapability(capability) }
                    }
            val model =
                profile
                    ?.selectableModels()
                    ?.filter { it.supportsCapability(capability) }
                    ?.sortedWith(
                        compareBy<ProviderModelConfig> {
                            if (it.id ==
                                profile.defaultModel
                            ) {
                                0
                            } else {
                                1
                            }
                        }.thenBy(ProviderModelConfig::id),
                    )?.firstOrNull()
            if (profile != null && model != null) {
                assignments[capability.name] = CatalogModelSelection(profile.id, model.id)
            }
        }
    }
    return if (assignments == taskModelAssignments) this else copy(taskModelAssignments = assignments)
}

internal fun ProviderAdapter.supportsTaskModel(capability: ModelCapability): Boolean =
    capability != ModelCapability.EMBEDDING || this == ProviderAdapter.OLLAMA || this == ProviderAdapter.OPENAI_COMPATIBLE
