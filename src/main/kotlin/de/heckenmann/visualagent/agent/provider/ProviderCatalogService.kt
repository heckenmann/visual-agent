package de.heckenmann.visualagent.agent.provider

import de.heckenmann.visualagent.config.AppConfig
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
) {
    private val json = Json { ignoreUnknownKeys = true }

    init {
        migrateLegacyConfiguration()
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
        save(state.copy(providers = providers))
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
        save(state.copy(activeProviderId = nextActive, providers = remaining))
        AppConfig.instance.llmProvider = nextActive
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
     * Persists the active provider identifier.
     *
     * Use cases: UC-0000007.
     */
    fun setActiveProvider(providerId: String) {
        require(getProvider(providerId)?.enabled == true) { "Provider is missing or disabled: $providerId" }
        val state = load()
        save(state.copy(activeProviderId = providerId))
        AppConfig.instance.llmProvider = providerId
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
        val selectedProviderId = providerId?.takeIf(String::isNotBlank) ?: activeProviderId()
        val provider =
            getProvider(selectedProviderId)?.takeIf(ProviderProfile::enabled)
                ?: error("Provider is missing or disabled: $selectedProviderId")
        val selectable = selectableModels(provider.id)
        val explicitModelId = modelId?.takeIf(String::isNotBlank)
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
        val config = AppConfig.instance
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
            )
        save(CatalogState(activeProviderId = config.normalizedProvider(), providers = profiles))
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
        val providers: List<ProviderProfile> = emptyList(),
    )

    private companion object {
        private const val KEY_CATALOG = "llm.provider.catalog.v1"
    }
}
