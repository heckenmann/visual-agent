package de.heckenmann.visualagent.agent.provider

import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.PreferenceStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service

/**
 * Stores provider profiles, model catalogs, and active selection in SQLite.
 *
 * Legacy Ollama/OpenAI preferences are migrated into profiles when no catalog exists.
 *
 * Use cases: UC-0000007, UC-0000008, UC-0000009.
 */
@Service
class ProviderCatalogService(
    private val preferenceStore: PreferenceStore,
    private val appConfig: AppConfigBean = AppConfigBean(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    init {
        migrateLegacyConfiguration()
        ensureBuiltInProfiles()
        migrateBuiltInCodexProfile()
    }

    /**
     * Returns every configured provider profile.
     *
     * Use cases: UC-0000007, UC-0000008.
     */
    fun listProviders(): List<ProviderProfile> = load().providers.sortedBy(ProviderProfile::name)

    /**
     * Returns enabled provider profiles.
     *
     * Use cases: UC-0000007, UC-0000008.
     */
    fun enabledProviders(): List<ProviderProfile> = listProviders().filter(ProviderProfile::enabled)

    /**
     * Returns one provider profile.
     *
     * Use cases: UC-0000008.
     */
    fun getProvider(id: String): ProviderProfile? = listProviders().firstOrNull { it.id == id }

    /**
     * Inserts or replaces a provider profile.
     *
     * Use cases: UC-0000008.
     */
    fun saveProvider(profile: ProviderProfile) {
        val state = load()
        val providers = state.providers.filterNot { it.id == profile.id } + profile
        require(providers.any(ProviderProfile::enabled)) { "At least one provider profile must remain enabled" }
        val nextActiveProviderId =
            state.activeProviderId
                .takeIf { activeId -> providers.any { it.id == activeId && it.enabled } }
                ?: providers.first(ProviderProfile::enabled).id
        val nextActiveModelId =
            if (nextActiveProviderId == state.activeProviderId) {
                state.activeModelId
            } else {
                providers.first { it.id == nextActiveProviderId }.defaultModel
            }
        save(state.copy(activeProviderId = nextActiveProviderId, activeModelId = nextActiveModelId, providers = providers))
        appConfig.llmProvider = nextActiveProviderId
    }

    /**
     * Deletes a provider profile when at least one other enabled profile remains.
     *
     * @return `true` when the profile was removed
     * @see docs/usecases/uc_0000008_manage_provider_profiles.md
     */
    fun deleteProvider(providerId: String): Boolean {
        val state = load()
        if (state.providers.none { it.id == providerId }) return false
        val remaining = state.providers.filterNot { it.id == providerId }
        val nextActive =
            if (state.activeProviderId == providerId) {
                remaining.firstOrNull(ProviderProfile::enabled)?.id ?: return false
            } else {
                state.activeProviderId
            }
        val nextActiveModel =
            if (nextActive == state.activeProviderId) state.activeModelId else remaining.first { it.id == nextActive }.defaultModel
        save(state.copy(activeProviderId = nextActive, activeModelId = nextActiveModel, providers = remaining))
        appConfig.llmProvider = nextActive
        return true
    }

    /**
     * Replaces discovered models while preserving configured model metadata and options.
     *
     * Use cases: UC-0000008, UC-0000009.
     */
    fun updateDiscoveredModels(
        providerId: String,
        modelIds: List<String>,
    ) {
        val profile = getProvider(providerId) ?: return
        val existing = profile.models.associateBy(ProviderModelConfig::id)
        val models =
            modelIds
                .distinct()
                .map { id -> existing[id] ?: ProviderModelConfig(id = id) }
        saveProvider(profile.copy(models = models))
    }

    /** Replaces discovered models while preserving provider-supplied display names. */
    fun updateDiscoveredModelConfigs(
        providerId: String,
        discoveredModels: List<ProviderModelConfig>,
    ) {
        val profile = getProvider(providerId) ?: return
        val existing = profile.models.associateBy(ProviderModelConfig::id)
        val models =
            discoveredModels
                .distinctBy(ProviderModelConfig::id)
                .map { discovered -> existing[discovered.id]?.copy(name = discovered.name) ?: discovered }
        saveProvider(profile.copy(models = models))
    }

    /**
     * Updates capabilities for existing models without replacing other model metadata.
     *
     * @param providerId Provider whose models should be updated
     * @param capabilities Map of model name to set of capability strings
     */
    fun updateModelCapabilities(
        providerId: String,
        capabilities: Map<String, Set<String>>,
    ) {
        val profile = getProvider(providerId) ?: return
        val models =
            profile.models.map { model ->
                val caps = capabilities[model.id] ?: model.capabilities
                if (caps != model.capabilities) model.copy(capabilities = caps) else model
            }
        saveProvider(profile.copy(models = models))
    }

    /**
     * Returns models that may be selected by the user.
     *
     * Use cases: UC-0000007, UC-0000009.
     */
    fun selectableModels(providerId: String): List<ProviderModelConfig> {
        val profile = getProvider(providerId) ?: return emptyList()
        return profile.models.filter { model ->
            model.status !in setOf(ModelStatus.DEPRECATED, ModelStatus.DISABLED) &&
                model.id !in profile.modelBlacklist &&
                (profile.modelWhitelist.isEmpty() || model.id in profile.modelWhitelist)
        }
    }

    /**
     * Returns the active provider identifier.
     *
     * Use cases: UC-0000007.
     */
    fun activeProviderId(): String = load().activeProviderId

    /**
     * Returns the model selected for the main agent.
     *
     * Use cases: UC-0000007.
     */
    fun activeModelId(): String {
        val state = load()
        return state.activeModelId.ifBlank { getProvider(state.activeProviderId)?.defaultModel.orEmpty() }
    }

    /**
     * Persists the active provider identifier.
     *
     * Use cases: UC-0000007.
     */
    fun setActiveProvider(providerId: String) {
        val provider = getProvider(providerId)?.takeIf(ProviderProfile::enabled)
        require(provider != null) { "Provider is missing or disabled: $providerId" }
        val state = load()
        save(state.copy(activeProviderId = providerId, activeModelId = provider.defaultModel))
        appConfig.llmProvider = providerId
    }

    /**
     * Persists the provider and model selected for the main agent without changing provider profile configuration.
     *
     * Use cases: UC-0000007.
     */
    fun setActiveSelection(
        providerId: String,
        modelId: String,
    ) {
        require(modelId.isNotBlank()) { "Model is required" }
        resolve(providerId, modelId)
        val state = load()
        save(state.copy(activeProviderId = providerId, activeModelId = modelId))
        appConfig.llmProvider = providerId
    }

    /**
     * Resolves one provider/model reference and merges options by specificity.
     *
     * Use cases: UC-0000007, UC-0000008.
     */
    fun resolve(
        providerId: String?,
        modelId: String?,
        variant: String? = null,
        agentOptions: Map<String, String> = emptyMap(),
    ): ResolvedModelConfig {
        val state = load()
        val explicitProviderId = providerId?.takeIf(String::isNotBlank)
        val selectedProviderId = explicitProviderId ?: state.activeProviderId
        val provider =
            getProvider(selectedProviderId)?.takeIf(ProviderProfile::enabled)
                ?: error("Provider is missing or disabled: $selectedProviderId")
        val selectable = selectableModels(provider.id)
        val explicitModelId =
            modelId?.takeIf(String::isNotBlank)
                ?: state.activeModelId.takeIf { explicitProviderId == null && it.isNotBlank() }
        val resolvedModelId =
            when {
                explicitModelId != null -> explicitModelId
                provider.defaultModel.isNotBlank() && selectable.any { it.id == provider.defaultModel } ->
                    provider.defaultModel
                else -> selectable.firstOrNull()?.id
            } ?: error("Provider ${provider.id} has no selectable model")
        val model =
            selectable.firstOrNull { it.id == resolvedModelId }
                ?: ProviderModelConfig(id = resolvedModelId).takeIf {
                    resolvedModelId !in provider.modelBlacklist &&
                        (provider.modelWhitelist.isEmpty() || resolvedModelId in provider.modelWhitelist)
                }
                ?: error("Model is missing, disabled, or filtered: ${provider.id}/$resolvedModelId")
        val modelDefaults =
            if (model.outputLimit != null) {
                mapOf("maxTokens" to model.outputLimit.toString()) + model.options
            } else {
                model.options
            }
        val variantOptions = variant?.let(model.variants::get).orEmpty()
        return ResolvedModelConfig(
            provider = provider,
            model = model,
            variant = variant?.takeIf(model.variants::containsKey),
            options = provider.options + modelDefaults + agentOptions + variantOptions,
        )
    }

    private fun migrateLegacyConfiguration() {
        if (preferenceStore.getPreference(KEY_CATALOG) != null) return
        val config = appConfig
        val profiles =
            listOf(
                ProviderProfile(
                    id = "ollama",
                    name = "Ollama",
                    adapter = ProviderAdapter.OLLAMA,
                    baseUrl = config.ollamaLocalUrl,
                    apiKey = config.ollamaApiKey,
                    defaultModel = config.ollamaModel,
                    models = listOf(ProviderModelConfig(config.ollamaModel)),
                ),
                ProviderProfile(
                    id = "openai",
                    name = "OpenAI",
                    adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                    baseUrl = config.openAiBaseUrl,
                    apiKey = config.openAiApiKey,
                    defaultModel = config.openAiModel,
                    models = listOf(ProviderModelConfig(config.openAiModel)),
                ),
                builtInCodexProfile(),
            )
        val activeProviderId = config.normalizedProvider()
        val activeModelId = if (activeProviderId == "openai") config.openAiModel else config.ollamaModel
        save(CatalogState(activeProviderId = activeProviderId, activeModelId = activeModelId, providers = profiles))
    }

    private fun ensureBuiltInProfiles() {
        if (preferenceStore.getPreference(KEY_CODEX_INITIALIZED) == "true") return
        val state = load()
        if (state.providers.none { it.id == ProviderEnvironmentCredentials.CODEX_PROFILE_ID }) {
            save(state.copy(providers = state.providers + builtInCodexProfile()))
        }
        preferenceStore.setPreference(KEY_CODEX_INITIALIZED, "true")
    }

    private fun builtInCodexProfile(): ProviderProfile =
        ProviderProfile(
            id = ProviderEnvironmentCredentials.CODEX_PROFILE_ID,
            name = "Codex CLI",
            adapter = ProviderAdapter.CODEX_CLI,
            baseUrl = "",
        )

    private fun migrateBuiltInCodexProfile() {
        val state = load()
        val providers =
            state.providers.map { profile ->
                if (profile.id == ProviderEnvironmentCredentials.CODEX_PROFILE_ID) {
                    profile.copy(
                        name = "Codex CLI",
                        adapter = ProviderAdapter.CODEX_CLI,
                        baseUrl = "",
                        apiKey = "",
                    )
                } else {
                    profile
                }
            }
        if (providers != state.providers) save(state.copy(providers = providers))
    }

    private fun load(): CatalogState =
        preferenceStore
            .getPreference(KEY_CATALOG)
            ?.let { encoded -> runCatching { json.decodeFromString<CatalogState>(encoded) }.getOrNull() }
            ?: CatalogState()

    private fun save(state: CatalogState) {
        preferenceStore.setPreference(KEY_CATALOG, json.encodeToString(state))
    }

    @Serializable
    private data class CatalogState(
        val version: Int = 1,
        val activeProviderId: String = "ollama",
        val activeModelId: String = "",
        val providers: List<ProviderProfile> = emptyList(),
    )

    private companion object {
        private const val KEY_CATALOG = "llm.provider.catalog.v1"
        private const val KEY_CODEX_INITIALIZED = "llm.provider.codex.profile.initialized.v1"
    }
}
