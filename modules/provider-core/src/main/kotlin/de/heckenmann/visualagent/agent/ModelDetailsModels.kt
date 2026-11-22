package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.provider.ProviderProfile
import kotlinx.serialization.Serializable

/**
 * Provider-neutral model detail response shaped after Ollama's `/api/show` payload.
 *
 * @property model Model identifier
 * @property modifiedAt Provider-reported modification timestamp
 * @property parameters Raw model parameter text, when available
 * @property template Prompt template text, when available
 * @property system System prompt text, when available
 * @property license License text, when available
 * @property details Structured model metadata
 * @property messages Optional template messages returned by the provider
 */
@Serializable
data class ShowResponse(
    val model: String,
    val modifiedAt: String,
    val parameters: String? = null,
    val template: String? = null,
    val system: String? = null,
    val license: String? = null,
    val details: ModelDetails? = null,
    val messages: List<Message>? = null,
)

/**
 * Structured model metadata returned by model detail endpoints.
 *
 * @property parentModel Optional parent model name
 * @property format Model file format
 * @property family Primary model family
 * @property families Additional model families
 * @property parameterSize Human-readable parameter count
 * @property quantizationLevel Quantization description
 * @property contextLimit Provider-reported context window in tokens
 * @property capabilities Provider-reported model capabilities
 * @property capabilitiesComplete Whether provider metadata declares the complete capability set
 * @property capabilitiesOverridden Whether the user set a legacy whole-set override
 * @property capabilityOverrides Per-capability user overrides
 */
@Serializable
data class ModelDetails(
    val parentModel: String? = null,
    val format: String? = null,
    val family: String? = null,
    val families: List<String>? = null,
    val parameterSize: String? = null,
    val quantizationLevel: String? = null,
    val contextLimit: Int? = null,
    val capabilities: Set<String> = emptySet(),
    val capabilitiesComplete: Boolean = false,
    val capabilitiesOverridden: Boolean = false,
    val capabilityOverrides: Map<String, Boolean> = emptyMap(),
)

/** Adds discovered and manually overridden capability metadata from the provider catalog. */
fun ShowResponse.withCatalogCapabilities(
    profile: ProviderProfile,
    modelId: String,
): ShowResponse {
    val model = profile.models.firstOrNull { it.id == modelId } ?: return this
    return copy(
        details =
            (details ?: ModelDetails()).copy(
                capabilities = model.capabilities,
                capabilitiesComplete = model.capabilitiesComplete,
                capabilitiesOverridden = model.capabilitiesOverridden,
                capabilityOverrides = model.capabilityOverrides,
            ),
    )
}
