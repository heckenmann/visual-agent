package de.heckenmann.visualagent.agent.codexdirect

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import de.heckenmann.visualagent.agent.provider.ServerTrustManagerProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import reactor.test.StepVerifier
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.test.assertEquals

class DirectOAuthHttpTlsTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `managed root permits local TLS while platform trust and hostname verification remain enforced`() {
        val store = certificate()
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, PASSWORD.toCharArray()) }
        val tls = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }
        val server =
            HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                httpsConfigurator = HttpsConfigurator(tls)
                createContext("/models") { exchange ->
                    val body = "synthetic-models".toByteArray()
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                start()
            }
        try {
            val roots = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
            val manager = roots.trustManagers.filterIsInstance<X509TrustManager>().single()
            val managed = DirectOAuthHttpConfiguration(ServerTrustManagerProvider { manager }).webClient()
            val platform = DirectOAuthHttpConfiguration().webClient()
            val address = "https://localhost:${server.address.port}/models"

            StepVerifier
                .create(
                    managed
                        .get()
                        .uri(address)
                        .retrieve()
                        .bodyToMono(String::class.java),
                ).expectNext("synthetic-models")
                .verifyComplete()
            StepVerifier
                .create(
                    platform
                        .get()
                        .uri(address)
                        .retrieve()
                        .bodyToMono(String::class.java),
                ).expectErrorMatches(::isHandshakeFailure)
                .verify()
            StepVerifier
                .create(
                    managed
                        .get()
                        .uri("https://127.0.0.1:${server.address.port}/models")
                        .retrieve()
                        .bodyToMono(String::class.java),
                ).expectErrorMatches(::isHandshakeFailure)
                .verify()
        } finally {
            server.stop(0)
        }
    }

    private fun isHandshakeFailure(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.take(16).any { it is SSLHandshakeException }

    private fun certificate(): KeyStore {
        val file = directory.resolve("server.p12")
        val executable = if (System.getProperty("os.name").startsWith("Windows")) "keytool.exe" else "keytool"
        val keytool = Path.of(System.getProperty("java.home"), "bin", executable)
        val process =
            ProcessBuilder(
                keytool.toString(),
                "-genkeypair",
                "-alias",
                "server",
                "-keyalg",
                "RSA",
                "-keysize",
                "2048",
                "-storetype",
                "PKCS12",
                "-keystore",
                file.toString(),
                "-storepass",
                PASSWORD,
                "-keypass",
                PASSWORD,
                "-dname",
                "CN=localhost",
                "-ext",
                "SAN=dns:localhost",
                "-validity",
                "1",
                "-noprompt",
            ).redirectErrorStream(true)
                .redirectOutput(directory.resolve("keytool.log").toFile())
                .start()
        assertEquals(0, process.waitFor())
        return KeyStore.getInstance("PKCS12").apply { Files.newInputStream(file).use { load(it, PASSWORD.toCharArray()) } }
    }

    private companion object {
        const val PASSWORD = "synthetic-test-password"
    }
}
