package de.heckenmann.visualagent.agent.openai

import de.heckenmann.visualagent.agent.TestProviderRuntimeConfig
import de.heckenmann.visualagent.agent.TestToolRegistry
import de.heckenmann.visualagent.agent.provider.ServerTrustManagerProvider
import org.junit.jupiter.api.Test
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenAiClientTlsTest {
    @Test
    fun `OpenAI http client installs server trust manager without changing hostname verification`() {
        var managerRequested = false
        val trustManagerProvider =
            ServerTrustManagerProvider {
                managerRequested = true
                testTrustManager()
            }
        val client = OpenAiClient(io.mockk.mockk(relaxed = true), TestToolRegistry(), TestProviderRuntimeConfig(), trustManagerProvider)
        val customizerMethod = OpenAiClient::class.java.getDeclaredMethod("tlsClientCustomizer")
        customizerMethod.isAccessible = true
        val customizer = customizerMethod.invoke(client) as OpenAiHttpClientBuilderCustomizer
        val builder = SpringAiOpenAiHttpClient.builder()

        customizer.customize(builder)
        val httpClient = builder.build()

        assertTrue(managerRequested)
        assertNotNull(httpClient.okHttpClient.sslSocketFactory)
        assertNotNull(httpClient.okHttpClient.hostnameVerifier)
    }

    private fun testTrustManager() =
        object : X509TrustManager {
            override fun checkClientTrusted(
                chain: Array<out X509Certificate>,
                authType: String,
            ) = Unit

            override fun checkServerTrusted(
                chain: Array<out X509Certificate>,
                authType: String,
            ) = Unit

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
}
