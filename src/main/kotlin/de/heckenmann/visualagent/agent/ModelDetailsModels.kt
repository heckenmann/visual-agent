package de.heckenmann.visualagent.agent

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
 */
@Serializable
data class ModelDetails(
    val parentModel: String? = null,
    val format: String? = null,
    val family: String? = null,
    val families: List<String>? = null,
    val parameterSize: String? = null,
    val quantizationLevel: String? = null,
)
