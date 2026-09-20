package de.heckenmann.visualagent.agent.ollama

import de.heckenmann.visualagent.agent.ListTagsResponse
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

private val logger = KotlinLogging.logger {}

/**
 * Fetches model capabilities from Ollama's `/api/tags` endpoint.
 *
 * Spring AI's `OllamaApi.Model` does not expose the `capabilities` field, so this
 * function makes a direct HTTP call to read the raw JSON response.
 *
 * @param profile Provider profile with base URL and optional API key
 * @return Map of model name to set of capability strings (e.g. "tools", "thinking")
 */
internal fun fetchModelCapabilitiesReactive(profile: ProviderProfile): Mono<Map<String, Set<String>>> =
    Mono
        .fromCallable {
            try {
                val url = java.net.URI("${profile.baseUrl.trimEnd('/')}/api/tags").toURL()
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                profile.apiKey.trim().takeIf { it.isNotEmpty() }?.let {
                    connection.setRequestProperty("Authorization", "Bearer $it")
                }
                val body = connection.inputStream.bufferedReader().readText()
                val json = Json { ignoreUnknownKeys = true }
                val response = json.decodeFromString<ListTagsResponse>(body)
                response.models.associate { it.name to it.capabilities }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to fetch model capabilities from ${profile.baseUrl}" }
                emptyMap()
            }
        }.subscribeOn(Schedulers.boundedElastic())
