package de.heckenmann.visualagent.agent.provider

import kotlinx.serialization.Serializable

/**
 * Supported runtime adapter used by a configurable provider profile.
 */
@Serializable
enum class ProviderAdapter {
    OLLAMA,
    OPENAI_COMPATIBLE,
}

/**
 * Availability state used to filter models from user selection.
 */
@Serializable
enum class ModelStatus {
    ACTIVE,
    ALPHA,
    BETA,
    DEPRECATED,
    DISABLED,
}

/**
 * Persisted model definition belonging to one provider profile.
 *
 * @property id Provider-facing model identifier
 * @property name Human-readable model name
 * @property status Availability state
 * @property options Model-level request options inherited by agents
 * @property contextLimit Optional context-window limit
 * @property outputLimit Optional output-token limit
 */
@Serializable
data class ProviderModelConfig(
    val id: String,
    val name: String = id,
    val status: ModelStatus = ModelStatus.ACTIVE,
    val options: Map<String, String> = emptyMap(),
    val variants: Map<String, Map<String, String>> = emptyMap(),
    val contextLimit: Int? = null,
    val outputLimit: Int? = null,
)

/**
 * Persisted provider profile including endpoint, credentials, models, and defaults.
 *
 * @property id Stable provider identifier referenced by sessions and agents
 * @property name Human-readable provider name
 * @property adapter Runtime protocol adapter
 * @property baseUrl Provider API endpoint
 * @property apiKey Optional plaintext key stored in SQLite by current product decision
 * @property enabled Whether the profile may be selected
 * @property defaultModel Default model identifier
 * @property options Provider-level request options inherited by models and agents
 * @property models Known provider models
 * @property modelWhitelist Optional model IDs allowed for selection
 * @property modelBlacklist Model IDs excluded from selection
 */
@Serializable
data class ProviderProfile(
    val id: String,
    val name: String,
    val adapter: ProviderAdapter,
    val baseUrl: String,
    val apiKey: String = "",
    val enabled: Boolean = true,
    val defaultModel: String = "",
    val options: Map<String, String> = emptyMap(),
    val models: List<ProviderModelConfig> = emptyList(),
    val modelWhitelist: Set<String> = emptySet(),
    val modelBlacklist: Set<String> = emptySet(),
)

/**
 * Fully resolved provider, model, and merged request options.
 *
 * @property provider Selected provider profile
 * @property model Selected model configuration
 * @property options Options merged in provider, model, then agent order
 */
data class ResolvedModelConfig(
    val provider: ProviderProfile,
    val model: ProviderModelConfig,
    val variant: String?,
    val options: Map<String, String>,
)
