package de.heckenmann.visualagent.agent.codexdirect

import de.heckenmann.visualagent.agent.provider.ServerTrustManagerProvider
import de.heckenmann.visualagent.agent.provider.resolveSslContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.JdkClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import java.net.http.HttpClient
import java.time.Duration

/** Shares managed certificate trust with OAuth exchange, JWKS, and direct model discovery. */
@Configuration(proxyBeanMethods = false)
internal class DirectOAuthHttpConfiguration(
    private val trust: ServerTrustManagerProvider? = null,
) {
    /** Creates a bounded, non-redirecting client so bearer credentials cannot follow another host. */
    @Bean("codexDirectWebClient")
    fun webClient(): WebClient {
        val http =
            HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .sslContext(trust.resolveSslContext())
                .build()
        return WebClient
            .builder()
            .clientConnector(JdkClientHttpConnector(http).apply { setReadTimeout(Duration.ofSeconds(30)) })
            .codecs { it.defaultCodecs().maxInMemorySize(1_048_576) }
            .build()
    }
}
