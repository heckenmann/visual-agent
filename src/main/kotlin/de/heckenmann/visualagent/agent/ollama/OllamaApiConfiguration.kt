package de.heckenmann.visualagent.agent.ollama

import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.config.AppConfig
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient

/**
 * Creates the Ollama API client with authentication for secured endpoints.
 */
@Configuration
class OllamaApiConfiguration {
    /**
     * Creates the shared Ollama API client.
     *
     * Authentication reads the current key for every request, so saving a changed key does not require a restart.
     *
     * @return Ollama API client configured with the persisted endpoint and optional bearer token
     */
    @Bean
    fun ollamaApi(): OllamaApi = createOllamaApi(AppConfig.instance)
}

internal fun createOllamaApi(config: AppConfig): OllamaApi = createOllamaApi(config.ollamaLocalUrl) { config.ollamaApiKey }

internal fun createOllamaApi(profile: ProviderProfile): OllamaApi = createOllamaApi(profile.baseUrl) { profile.apiKey }

private fun createOllamaApi(
    baseUrl: String,
    apiKey: () -> String,
): OllamaApi =
    OllamaApi
        .builder()
        .baseUrl(baseUrl.ifBlank { "http://localhost:11434" }.trimEnd('/'))
        .restClientBuilder(
            RestClient
                .builder()
                .requestInterceptor { request, body, execution ->
                    applyOllamaAuthentication(request.headers, apiKey())
                    execution.execute(request, body)
                },
        ).webClientBuilder(
            WebClient
                .builder()
                .filter(
                    ExchangeFilterFunction.ofRequestProcessor { request ->
                        val authenticatedRequest =
                            ClientRequest
                                .from(request)
                                .headers { headers -> applyOllamaAuthentication(headers, apiKey()) }
                                .build()
                        reactor.core.publisher.Mono
                            .just(authenticatedRequest)
                    },
                ),
        ).build()

internal fun applyOllamaAuthentication(
    headers: HttpHeaders,
    apiKey: String,
) {
    apiKey.trim().takeIf { it.isNotEmpty() }?.let(headers::setBearerAuth)
}
