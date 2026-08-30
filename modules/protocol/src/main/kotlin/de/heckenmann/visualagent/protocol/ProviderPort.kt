package de.heckenmann.visualagent.protocol

/** Provider catalog and model discovery operations exposed to the UI. */
interface ProviderPort {
    /** Lists every configured provider profile. */
    fun listProviders(): List<ProviderProfile>

    /** Lists enabled provider profiles. */
    fun enabledProviders(): List<ProviderProfile>

    /** Returns one provider profile, or null when absent. */
    fun getProvider(id: String): ProviderProfile?

    /** Returns models selectable for one provider. */
    fun selectableModels(providerId: String): List<ProviderModel>

    /** Returns the active provider identifier. */
    fun activeProviderId(): String

    /** Returns the active model identifier. */
    fun activeModelId(): String

    /** Activates a provider without selecting an explicit model. */
    fun setActiveProvider(providerId: String)

    /** Activates a provider/model selection. */
    fun setActiveSelection(
        providerId: String,
        modelId: String,
    )

    /** Persists or replaces a provider profile. */
    fun saveProvider(profile: ProviderProfile)

    /** Deletes a provider profile when the catalog allows it. */
    fun deleteProvider(providerId: String): Boolean

    /** Discovers models and updates the provider catalog. */
    suspend fun refreshModels(providerId: String): List<ProviderModel>

    /** Discovers models with a staged provider profile without persisting the result. */
    suspend fun discoverModels(profile: ProviderProfile): List<ProviderModel>

    /** Loads details for one provider model. */
    suspend fun modelDetails(
        providerId: String,
        modelId: String,
    ): ModelDetails

    /** Registers a listener for catalog changes. */
    fun addChangeListener(listener: () -> Unit): AutoCloseable
}

/** Supported provider adapter. */
enum class ProviderAdapter {
    OLLAMA,
    OPENAI_COMPATIBLE,
    CODEX_CLI,
}

/** Availability state used for model selection. */
enum class ModelStatus {
    ACTIVE,
    ALPHA,
    BETA,
    DEPRECATED,
    DISABLED,
}

/** Configured provider profile. */
data class ProviderProfile(
    val id: String,
    val name: String,
    val adapter: ProviderAdapter,
    val baseUrl: String,
    val apiKey: String = "",
    val enabled: Boolean = true,
    val defaultModel: String = "",
    val options: Map<String, String> = emptyMap(),
    val models: List<ProviderModel> = emptyList(),
    val modelWhitelist: Set<String> = emptySet(),
    val modelBlacklist: Set<String> = emptySet(),
)

/** Configured model metadata. */
data class ProviderModel(
    val id: String,
    val name: String = id,
    val status: ModelStatus = ModelStatus.ACTIVE,
    val options: Map<String, String> = emptyMap(),
    val variants: Map<String, Map<String, String>> = emptyMap(),
    val contextLimit: Int? = null,
    val outputLimit: Int? = null,
    val capabilities: Set<String> = emptySet(),
)

/** User-safe model detail response. */
data class ModelDetails(
    val model: String,
    val modifiedAt: String,
    val family: String? = null,
    val parameterSize: String? = null,
    val format: String? = null,
    val quantizationLevel: String? = null,
)
