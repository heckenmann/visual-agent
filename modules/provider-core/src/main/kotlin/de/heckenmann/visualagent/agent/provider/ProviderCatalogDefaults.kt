package de.heckenmann.visualagent.agent.provider

/** Constructs the first catalog from legacy configuration without replacing an existing catalog. */
internal fun legacyProviderCatalog(config: ProviderRuntimeConfig): CatalogState {
    val providers =
        listOf(
            ProviderProfile(
                id = "ollama",
                name = "Ollama",
                adapter = ProviderAdapter.OLLAMA,
                baseUrl = config.ollamaLocalUrl,
                apiKey = config.ollamaApiKey,
                defaultModel = config.ollamaModel,
                models = config.ollamaModel.toModelConfigs(),
            ),
            ProviderProfile(
                id = "openai",
                name = "OpenAI",
                adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                baseUrl = config.openAiBaseUrl,
                apiKey = config.openAiApiKey,
                defaultModel = config.openAiModel,
                models = config.openAiModel.toModelConfigs(),
            ),
            builtInCodexProfile(),
        )
    val activeProviderId = config.normalizedProvider()
    return CatalogState(
        activeProviderId = activeProviderId,
        activeModelId = if (activeProviderId == "openai") config.openAiModel else config.ollamaModel,
        providers = providers,
    )
}

/** Returns the fixed built-in Codex connection profile. */
internal fun builtInCodexProfile(): ProviderProfile =
    ProviderProfile(
        id = ProviderEnvironmentCredentials.CODEX_PROFILE_ID,
        name = "Codex CLI",
        adapter = ProviderAdapter.CODEX_CLI,
        baseUrl = "",
    )

/** Normalizes legacy built-in Codex connection fields. */
internal fun CatalogState.migrateCodexProfile(): CatalogState =
    copy(
        providers =
            providers.map { profile ->
                if (profile.id == ProviderEnvironmentCredentials.CODEX_PROFILE_ID) {
                    profile.copy(name = "Codex CLI", adapter = ProviderAdapter.CODEX_CLI, baseUrl = "", apiKey = "")
                } else {
                    profile
                }
            },
    )

/** Makes an already selected Codex model selectable without inventing a model identifier. */
internal fun CatalogState.withSelectableActiveCodexModel(): CatalogState {
    val activeModel = activeModelId.takeIf(String::isNotBlank) ?: return this
    return copy(
        providers =
            providers.map { profile ->
                if (profile.id == activeProviderId &&
                    profile.adapter == ProviderAdapter.CODEX_CLI &&
                    profile.defaultModel.isBlank() &&
                    profile.models.none { it.id == activeModel }
                ) {
                    profile.copy(
                        defaultModel = activeModel,
                        models = listOf(ProviderModelConfig(activeModel, capabilities = setOf("vision"))),
                    )
                } else {
                    profile
                }
            },
    )
}

/** Retains a configured Codex default in its selectable model catalog. */
internal fun ProviderProfile.withSelectableCodexDefault(): ProviderProfile =
    if (adapter == ProviderAdapter.CODEX_CLI && defaultModel.isNotBlank() && models.none { it.id == defaultModel }) {
        copy(models = models + ProviderModelConfig(defaultModel, capabilities = setOf("vision")))
    } else {
        this
    }

/** Applies model availability and profile filters to one consistent snapshot. */
internal fun ProviderProfile.selectableModels(): List<ProviderModelConfig> =
    models.filter { model ->
        model.status !in setOf(ModelStatus.DEPRECATED, ModelStatus.DISABLED) &&
            model.id !in modelBlacklist &&
            (modelWhitelist.isEmpty() || model.id in modelWhitelist)
    }

private fun String.toModelConfigs(): List<ProviderModelConfig> =
    takeIf(String::isNotBlank)?.let(::ProviderModelConfig)?.let(::listOf).orEmpty()
