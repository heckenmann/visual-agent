package de.heckenmann.visualagent.update

import de.heckenmann.visualagent.agent.provider.ServerTrustManagerProvider
import org.junit.jupiter.api.Test
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import kotlin.test.assertTrue

class GitHubReleaseClientTlsTest {
    @Test
    fun `release metadata and artifact client uses server trust manager`() {
        var requested = false
        val provider =
            ServerTrustManagerProvider {
                requested = true
                testTrustManager()
            }

        GitHubReleaseHttpClient(serverTrustManagerProvider = provider)

        assertTrue(requested)
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
