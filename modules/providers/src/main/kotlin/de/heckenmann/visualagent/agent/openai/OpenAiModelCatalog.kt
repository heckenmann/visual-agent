package de.heckenmann.visualagent.agent.openai

import com.openai.client.OpenAIClient
import java.net.URI

/**
 * Loads and filters models from an OpenAI-compatible SDK client.
 *
 * @property clientFactory Creates one short-lived client for each model refresh
 */
internal class OpenAiModelCatalog(
    private val clientFactory: () -> OpenAIClient,
) {
    /**
     * Fetches models and closes the SDK client after success or failure.
     *
     * @param modelsUri Endpoint URI used to apply provider-specific filtering
     * @return Sorted model identifiers supported by the chat integration
     */
    fun load(modelsUri: URI): List<String> {
        val client = clientFactory()
        try {
            val modelIds =
                client
                    .models()
                    .list()
                    .data()
                    .map { it.id() }
            return OpenAiModelFilter.filter(modelIds, modelsUri)
        } finally {
            client.close()
        }
    }
}
