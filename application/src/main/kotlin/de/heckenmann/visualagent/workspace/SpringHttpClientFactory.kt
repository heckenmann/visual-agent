package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.agent.provider.ServerTrustManagerProvider
import de.heckenmann.visualagent.agent.provider.resolveTrustManager
import org.apache.hc.client5.http.DnsResolver
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy
import org.apache.hc.core5.util.Timeout
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.InetAddress
import java.time.Duration
import javax.net.ssl.SSLContext

/** Builds Spring RestClient instances with explicit trust, DNS, redirect, and timeout policy. */
internal object SpringHttpClientFactory {
    /** Creates a RestClient whose DNS resolver validates and pins the exact address set per connection. */
    fun create(
        trustManagerProvider: ServerTrustManagerProvider?,
        addressResolver: (String) -> Array<InetAddress>,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
    ): ManagedRestClient {
        val trustManager = trustManagerProvider.resolveTrustManager()
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
        val dnsResolver =
            object : DnsResolver {
                override fun resolve(host: String): Array<InetAddress> = addressResolver(host)

                override fun resolveCanonicalHostname(host: String): String = host.lowercase()
            }
        val connectionManager =
            PoolingHttpClientConnectionManagerBuilder
                .create()
                .setDnsResolver(dnsResolver)
                .setTlsSocketStrategy(
                    ClientTlsStrategyBuilder
                        .create()
                        .setSslContext(sslContext)
                        .setHostVerificationPolicy(HostnameVerificationPolicy.BUILTIN)
                        .buildClassic(),
                ).setDefaultConnectionConfig(
                    ConnectionConfig
                        .custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMillis.toLong()))
                        .setSocketTimeout(Timeout.ofMilliseconds(readTimeoutMillis.toLong()))
                        .build(),
                ).build()
        val requestConfig =
            RequestConfig
                .custom()
                .setRedirectsEnabled(false)
                .setResponseTimeout(Timeout.ofMilliseconds(readTimeoutMillis.toLong()))
                .build()
        val httpClient: CloseableHttpClient =
            HttpClients
                .custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .build()
        val requestFactory =
            HttpComponentsClientHttpRequestFactory(httpClient).apply {
                setConnectionRequestTimeout(Duration.ofMillis(readTimeoutMillis.toLong()))
                setReadTimeout(Duration.ofMillis(readTimeoutMillis.toLong()))
            }
        return ManagedRestClient(RestClient.builder().requestFactory(requestFactory).build(), httpClient)
    }
}

/** Couples a Spring RestClient with its owned Apache transport lifecycle. */
internal class ManagedRestClient(
    val client: RestClient,
    private val httpClient: CloseableHttpClient,
) : AutoCloseable {
    override fun close() {
        try {
            httpClient.close()
        } catch (error: Exception) {
            throw IllegalStateException("Unable to close HTTP client", error)
        }
    }
}
