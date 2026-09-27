package de.heckenmann.visualagent.security

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import de.heckenmann.visualagent.agent.ollama.OllamaApiConfiguration
import de.heckenmann.visualagent.agent.openai.OpenAiClient
import de.heckenmann.visualagent.agent.openai.OpenAiPromptFactory
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.update.GitHubReleaseAsset
import de.heckenmann.visualagent.update.GitHubReleaseHttpClient
import de.heckenmann.visualagent.workspace.SpringHttpClientFactory
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.core.io.buffer.DataBufferUtils
import java.net.InetSocketAddress
import java.nio.file.Path
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import kotlin.test.assertEquals
import kotlin.test.assertFails

class ManagedTlsOutboundClientTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `Spring AI OpenAI chat client trusts managed CA and verifies hostname`() {
        val material = tlsMaterial()
        val server = httpsServer(material)
        val appConfig = AppConfigBean().apply { openAiApiKey = "test-key" }
        val client =
            OpenAiClient(
                mockk<OpenAiPromptFactory>(relaxed = true),
                mockk<ProviderToolCallbacks>(relaxed = true),
                appConfig,
                material,
            )
        val model =
            OpenAiClient::class.java
                .getDeclaredMethod("chatModel", ProviderProfile::class.java, String::class.java)
                .apply { isAccessible = true }
                .invoke(
                    client,
                    ProviderProfile(
                        id = "test-openai",
                        name = "Test OpenAI",
                        adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                        baseUrl = "https://localhost:${server.server.address.port}/v1",
                        apiKey = "test-key",
                        defaultModel = "test-model",
                    ),
                    "test-model",
                ) as org.springframework.ai.chat.model.ChatModel

        try {
            model.call(Prompt(listOf(UserMessage("hello"))))
            assertEquals(1, server.requestCount.get())

            appConfig.openAiBaseUrl = "https://127.0.0.1:${server.server.address.port}/v1"
            val mismatchedModel =
                OpenAiClient::class.java
                    .getDeclaredMethod("chatModel", ProviderProfile::class.java, String::class.java)
                    .apply { isAccessible = true }
                    .invoke(
                        client,
                        ProviderProfile(
                            id = "test-openai-ip",
                            name = "Test OpenAI IP",
                            adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                            baseUrl = appConfig.openAiBaseUrl,
                            apiKey = "test-key",
                            defaultModel = "test-model",
                        ),
                        "test-model",
                    ) as org.springframework.ai.chat.model.ChatModel
            assertFails { mismatchedModel.call(Prompt(listOf(UserMessage("hello")))) }
        } finally {
            server.server.stop(0)
        }
    }

    @Test
    fun `Ollama Spring clients trust managed CA for their real HTTP request`() {
        val material = tlsMaterial()
        val server = httpsServer(material)
        val appConfig = AppConfigBean().apply { ollamaLocalUrl = "https://localhost:${server.server.address.port}" }

        try {
            OllamaApiConfiguration(appConfig, material).ollamaApi().listModels()
            assertEquals(1, server.requestCount.get())
        } finally {
            server.server.stop(0)
        }
    }

    @Test
    fun `shared Spring RestClient transport trusts managed CA and verifies hostname`() {
        val material = tlsMaterial()
        val server = httpsServer(material)
        val client =
            SpringHttpClientFactory.create(
                material,
                { host -> java.net.InetAddress.getAllByName(host) },
                5_000,
                5_000,
            )

        try {
            val body =
                client.client
                    .get()
                    .uri("https://localhost:${server.server.address.port}/")
                    .retrieve()
                    .body(String::class.java)
            assertEquals("{\"models\": []}", body)
            assertEquals(1, server.requestCount.get())

            assertFails {
                client.client
                    .get()
                    .uri("https://127.0.0.1:${server.server.address.port}/")
                    .retrieve()
                    .body(String::class.java)
            }
            assertEquals(1, server.requestCount.get())
        } finally {
            client.close()
            server.server.stop(0)
        }
    }

    @Test
    fun `release artifact download trusts managed CA and rejects a hostname mismatch`() {
        val material = tlsMaterial()
        val server = httpsServer(material)
        val client = GitHubReleaseHttpClient(serverTrustManagerProvider = material)

        try {
            val body =
                client
                    .download(GitHubReleaseAsset("release.jar", 0, null, "https://localhost:${server.server.address.port}/release.jar"))
                    .map { buffer ->
                        val bytes = ByteArray(buffer.readableByteCount())
                        try {
                            buffer.read(bytes)
                            String(bytes)
                        } finally {
                            DataBufferUtils.release(buffer)
                        }
                    }.collectList()
                    .block()
                    ?.joinToString("")
            assertEquals("{\"models\": []}", body)
            assertEquals(1, server.requestCount.get())

            assertFails {
                client
                    .download(GitHubReleaseAsset("release.jar", 0, null, "https://127.0.0.1:${server.server.address.port}/release.jar"))
                    .collectList()
                    .block()
            }
            assertEquals(1, server.requestCount.get())
        } finally {
            server.server.stop(0)
        }
    }

    private fun tlsMaterial(): ManagedTlsMaterialService {
        val files = ManagedTlsStoreFiles(temporaryDirectory)
        val audit = TlsMutationAudit()
        val material = ManagedTlsMaterialService(files, audit, ManagedTlsCertificateOperations(files, audit))
        val ca = material.generateCertificate("test-ca", "CN=Test CA", emptyList(), emptyList(), true, 30)
        material.generateCertificate("localhost", "CN=localhost", listOf("localhost"), emptyList(), false, 30, ca.alias)
        material.importTrustedCertificate("test-ca", material.exportPublicCertificate(ca.alias)!!)
        return material
    }

    private fun httpsServer(material: ManagedTlsMaterialService): LocalHttpsServer {
        val keyManagerFactory = material.keyManagerFactory("localhost")
        val serverContext =
            SSLContext.getInstance("TLS").apply {
                init(keyManagerFactory.keyManagers, null, SecureRandom())
            }
        val requestCount = AtomicInteger()
        val server =
            HttpsServer
                .create(InetSocketAddress("localhost", 0), 0)
                .apply {
                    httpsConfigurator = HttpsConfigurator(serverContext)
                    createContext("/") { exchange ->
                        requestCount.incrementAndGet()
                        val body = responseFor(exchange.requestURI.path).toByteArray()
                        exchange.responseHeaders.add("Content-Type", "application/json")
                        exchange.sendResponseHeaders(200, body.size.toLong())
                        exchange.responseBody.use { it.write(body) }
                    }
                    start()
                }
        return LocalHttpsServer(server, requestCount)
    }

    private fun responseFor(path: String): String =
        if (path.endsWith("/chat/completions")) {
            """{"id":"chatcmpl-test","object":"chat.completion","created":1,"model":"test-model","choices":[{"index":0,"message":{"role":"assistant","content":"connected"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}"""
        } else {
            """{"models": []}"""
        }
}

private data class LocalHttpsServer(
    val server: HttpsServer,
    val requestCount: AtomicInteger,
)
