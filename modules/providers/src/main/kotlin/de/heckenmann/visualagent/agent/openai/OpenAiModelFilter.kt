package de.heckenmann.visualagent.agent.openai

import de.heckenmann.visualagent.agent.provider.ModelCapability
import de.heckenmann.visualagent.agent.provider.OpenAiModelCapabilities
import java.net.URI

/**
 * Filters model-list responses to models usable by the text chat integration.
 */
internal object OpenAiModelFilter {
    internal fun filter(
        modelIds: List<String>,
        modelsUri: URI,
    ): List<String> {
        val candidates =
            modelIds
                .map(String::trim)
                .filter(String::isNotBlank)
                .filter { ModelCapability.CHAT in OpenAiModelCapabilities.infer(it) }

        return candidates
            .filter { !isOfficialOpenAiEndpoint(modelsUri) || isOfficialChatModel(it) }
            .distinct()
            .sorted()
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
