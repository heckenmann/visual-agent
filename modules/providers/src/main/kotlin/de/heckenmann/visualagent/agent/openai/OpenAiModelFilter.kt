package de.heckenmann.visualagent.agent.openai

import java.net.URI

/**
 * Filters model-list responses to models usable by the text chat integration.
 */
internal object OpenAiModelFilter {
    private val unsupportedCapabilities =
        listOf(
            "audio",
            "embedding",
            "image",
            "moderation",
            "realtime",
            "speech",
            "transcribe",
            "tts",
            "whisper",
        )

    internal fun filter(
        modelIds: List<String>,
        modelsUri: URI,
    ): List<String> {
        val candidates =
            modelIds
                .map(String::trim)
                .filter(String::isNotBlank)
                .filterNot(::hasUnsupportedCapability)

        return candidates
            .filter { !isOfficialOpenAiEndpoint(modelsUri) || isOfficialChatModel(it) }
            .distinct()
            .sorted()
    }

    private fun hasUnsupportedCapability(modelId: String): Boolean {
        val normalized = modelId.lowercase()
        return unsupportedCapabilities.any { capability ->
            normalized.contains(capability)
        }
    }

    private fun isOfficialChatModel(modelId: String): Boolean {
        val normalized = modelId.lowercase()
        val baseModel = normalized.substringAfter("ft:", normalized)
        return baseModel.startsWith("gpt-") ||
            baseModel.startsWith("chatgpt-") ||
            Regex("""o[1-9](?:-|$)""").containsMatchIn(baseModel)
    }

    private fun isOfficialOpenAiEndpoint(modelsUri: URI): Boolean = modelsUri.host.equals("api.openai.com", ignoreCase = true)
}
