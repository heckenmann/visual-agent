package de.heckenmann.visualagent.agent.provider

import kotlinx.serialization.Serializable

/** Provider-neutral model features used for explicit, independently configurable agent work. */
@Serializable
enum class ModelCapability {
    CHAT,
    VISION,
    AUDIO_INPUT,
    AUDIO_OUTPUT,
    IMAGE_GENERATION,
    EMBEDDING,
    TOOLS,
    ;

    companion object {
        /** Normalizes provider-reported capability strings without dropping unknown metadata. */
        fun fromProviderValue(raw: String): ModelCapability? =
            when (raw.trim().lowercase().replace('-', '_')) {
                "chat", "completion", "generate_text" -> CHAT
                "vision", "image_input" -> VISION
                "audio_input", "transcription", "transcribe" -> AUDIO_INPUT
                "audio_output", "speech", "tts" -> AUDIO_OUTPUT
                "image_generation", "image_output" -> IMAGE_GENERATION
                "embedding", "embeddings" -> EMBEDDING
                "tools", "tool_calling", "function_calling" -> TOOLS
                else -> null
            }
    }
}

/**
 * Classifies OpenAI model families where the model-list API contains no capability metadata.
 * Unknown/custom models are intentionally *not* granted specialized capabilities implicitly.
 */
object OpenAiModelCapabilities {
    /** Returns the known capabilities for this model identifier, or only CHAT for unknown models. */
    fun infer(modelId: String): Set<ModelCapability> {
        val id = modelId.lowercase().substringAfter("ft:", modelId.lowercase())
        return when {
            id.startsWith("text-embedding-") || id.startsWith("embedding-") -> setOf(ModelCapability.EMBEDDING)
            id.startsWith("whisper-") || id.contains("-transcribe") -> setOf(ModelCapability.AUDIO_INPUT)
            id.startsWith("tts-") || id.contains("-tts") -> setOf(ModelCapability.AUDIO_OUTPUT)
            id.startsWith("dall-e-") || id.startsWith("gpt-image-") || id.startsWith("image-") ->
                setOf(ModelCapability.IMAGE_GENERATION)
            id.startsWith("gpt-realtime") -> setOf(ModelCapability.CHAT, ModelCapability.AUDIO_INPUT, ModelCapability.AUDIO_OUTPUT)
            id.startsWith("gpt-4o-audio") ->
                setOf(ModelCapability.CHAT, ModelCapability.AUDIO_INPUT, ModelCapability.AUDIO_OUTPUT, ModelCapability.TOOLS)
            id.startsWith("gpt-4o") ||
                id.startsWith("gpt-4.1") ||
                id.startsWith("gpt-5") ||
                id.startsWith("gpt-6") ->
                setOf(ModelCapability.CHAT, ModelCapability.VISION, ModelCapability.TOOLS)
            id.startsWith("gpt-") || id.startsWith("chatgpt-") || Regex("""^o[1-9]""").containsMatchIn(id) ->
                setOf(ModelCapability.CHAT)
            else -> setOf(ModelCapability.CHAT)
        }
    }
}

/** Resolves normalized feature declarations in the legacy persisted provider-capability format. */
fun ProviderModelConfig.supportsCapability(capability: ModelCapability): Boolean =
    capabilities.any { ModelCapability.fromProviderValue(it) == capability } ||
        (!capabilitiesComplete && capability == ModelCapability.CHAT)
