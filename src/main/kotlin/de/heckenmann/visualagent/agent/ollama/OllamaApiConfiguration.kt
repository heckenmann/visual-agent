package de.heckenmann.visualagent.agent.ollama

import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.config.AppConfigBean
import io.netty.channel.ChannelOption
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.ReactorClientHttpRequestFactory
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.client.RestClient
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

/**
 * Creates the Ollama API client with authentication for secured endpoints.
 */
@Configuration
class OllamaApiConfiguration(
    private val appConfig: AppConfigBean,
) {
    /**
     * Creates the shared Ollama API client.
     *
     * Authentication reads the current key for every request, so saving a changed key does not require a restart.
     *
     * @return Ollama API client configured with the persisted endpoint and optional bearer token
     */
    @Bean
    fun ollamaApi(): OllamaApi = createOllamaApi(appConfig)
}

internal fun createOllamaApi(config: AppConfigBean): OllamaApi =
    createOllamaApi(
        baseUrl = config.ollamaLocalUrl,
        timeoutSeconds = config.timeoutSeconds,
        apiKey = { config.ollamaApiKey },
    )

internal fun createOllamaApi(
    profile: ProviderProfile,
    appConfig: AppConfigBean,
): OllamaApi =
    createOllamaApi(
        baseUrl = profile.baseUrl,
        timeoutSeconds = profile.options["timeoutSeconds"]?.toIntOrNull() ?: appConfig.timeoutSeconds,
        apiKey = { profile.apiKey },
    )

private fun createOllamaApi(
    baseUrl: String,
    timeoutSeconds: Int,
    apiKey: () -> String,
): OllamaApi {
    val effectiveTimeout = timeoutSeconds.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS)
    val httpClient =
        HttpClient
            .create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
            .responseTimeout(Duration.ofSeconds(effectiveTimeout.toLong()))
    val restConnector = ReactorClientHttpRequestFactory(httpClient)
    val webConnector = ReactorClientHttpConnector(httpClient)
    return OllamaApi
        .builder()
        .baseUrl(baseUrl.ifBlank { "http://localhost:11434" }.trimEnd('/'))
        .restClientBuilder(
            RestClient
                .builder()
                .requestFactory(restConnector)
                .requestInterceptor { request, body, execution ->
                    applyOllamaAuthentication(request.headers, apiKey())
                    execution.execute(request, body)
                },
        ).webClientBuilder(
            WebClient
                .builder()
                .clientConnector(webConnector)
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
}

internal fun applyOllamaAuthentication(
    headers: HttpHeaders,
    apiKey: String,
) {
    apiKey.trim().takeIf { it.isNotEmpty() }?.let(headers::setBearerAuth)
}

private const val MIN_TIMEOUT_SECONDS = 30
private const val MAX_TIMEOUT_SECONDS = 600
private const val CONNECT_TIMEOUT_MILLIS = 10_000
