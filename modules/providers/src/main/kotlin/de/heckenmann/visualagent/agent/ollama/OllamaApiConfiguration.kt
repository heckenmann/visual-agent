package de.heckenmann.visualagent.agent.ollama

import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.provider.ProviderRuntimeConfig
import de.heckenmann.visualagent.agent.provider.ServerTrustManagerProvider
import de.heckenmann.visualagent.agent.provider.resolveSslContext
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.http.client.reactive.JdkClientHttpConnector
import org.springframework.web.client.RestClient
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import java.net.http.HttpClient
import java.time.Duration

/**
 * Creates authenticated Ollama API clients for synchronous and streaming endpoints.
 *
 * [RestClient] serves synchronous Ollama API calls, while [WebClient] serves
 * [OllamaApi.streamingChat]. Authentication reads the current key for every request.
 */
@Configuration(proxyBeanMethods = false)
class OllamaApiConfiguration(
    private val appConfig: ProviderRuntimeConfig,
    private val serverTrustManagerProvider: ServerTrustManagerProvider? = null,
) {
    /**
     * Creates the shared Ollama API client.
     *
     * Authentication reads the current key for every request, so saving a changed key does not require a restart.
     *
     * @return Ollama API client configured with the persisted endpoint and optional bearer token
     */
    @Bean
    fun ollamaApi(): OllamaApi = createOllamaApi(appConfig, serverTrustManagerProvider)

    /** Creates the fallback model from the same authenticated, managed-TLS API client. */
    @Bean
    fun ollamaChatModel(ollamaApi: OllamaApi): OllamaChatModel = OllamaChatModel.builder().ollamaApi(ollamaApi).build()
}

internal fun createOllamaApi(
    config: ProviderRuntimeConfig,
    serverTrustManagerProvider: ServerTrustManagerProvider? = null,
): OllamaApi =
    createOllamaApi(
        baseUrl = config.ollamaLocalUrl,
        timeoutSeconds = config.timeoutSeconds,
        apiKey = { config.ollamaApiKey },
        serverTrustManagerProvider = serverTrustManagerProvider,
    )

internal fun createOllamaApi(
    profile: ProviderProfile,
    appConfig: ProviderRuntimeConfig,
    serverTrustManagerProvider: ServerTrustManagerProvider? = null,
): OllamaApi =
    createOllamaApi(
        baseUrl = profile.baseUrl,
        timeoutSeconds = profile.options["timeoutSeconds"]?.toIntOrNull() ?: appConfig.timeoutSeconds,
        apiKey = { profile.apiKey },
        serverTrustManagerProvider = serverTrustManagerProvider,
    )

private fun createOllamaApi(
    baseUrl: String,
    timeoutSeconds: Int,
    apiKey: () -> String,
    serverTrustManagerProvider: ServerTrustManagerProvider? = null,
): OllamaApi {
    val effectiveTimeout = timeoutSeconds.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS)
    val httpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MILLIS.toLong()))
            .followRedirects(HttpClient.Redirect.NEVER)
            .sslContext(serverTrustManagerProvider.resolveSslContext())
            .build()
    val readTimeout = Duration.ofSeconds(effectiveTimeout.toLong())
    val restConnector = JdkClientHttpRequestFactory(httpClient).apply { setReadTimeout(readTimeout) }
    val webConnector = JdkClientHttpConnector(httpClient).apply { setReadTimeout(readTimeout) }
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
