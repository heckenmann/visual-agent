package de.heckenmann.visualagent.agent.openai

import com.sun.net.httpserver.HttpServer
import de.heckenmann.visualagent.agent.TestProviderRuntimeConfig
import de.heckenmann.visualagent.agent.TestToolRegistry
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import io.mockk.mockk
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals

/** Verifies embedding requests use the selected profile endpoint and model. */
class OpenAiEmbeddingTest {
    @Test
    fun `embeddings use the selected endpoint model and credentials`() =
        runTest {
            val request = AtomicReference<String>()
            val authorization = AtomicReference<String>()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/v1/embeddings") { exchange ->
                exchange.use {
                    request.set(it.requestBody.bufferedReader().use { reader -> reader.readText() })
                    authorization.set(it.requestHeaders.getFirst("Authorization"))
                    val response =
                        """
                        {"object":"list","data":[{"object":"embedding","index":0,"embedding":[0.25,0.5]}],
                        "model":"embed-test","usage":{"prompt_tokens":1,"total_tokens":1}}
                        """.trimIndent().toByteArray()
                    it.responseHeaders.set("Content-Type", "application/json")
                    it.sendResponseHeaders(200, response.size.toLong())
                    it.responseBody.write(response)
                }
            }
            server.start()
            try {
                val profile =
                    ProviderProfile(
                        id = "embedding-provider",
                        name = "Embedding provider",
                        adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                        baseUrl = "http://127.0.0.1:${server.address.port}",
                        apiKey = "test-profile-key",
                        defaultModel = "chat-default",
                    )
                val client = OpenAiClient(mockk(), TestToolRegistry(), TestProviderRuntimeConfig())

                assertEquals(listOf(0.25, 0.5), client.embeddingsReactive("probe text", "embed-test", profile).awaitSingle())
                val body = Json.parseToJsonElement(request.get()).jsonObject
                assertEquals("embed-test", body.getValue("model").jsonPrimitive.content)
                assertEquals("probe text", body.getValue("input").jsonPrimitive.content)
                assertEquals("Bearer test-profile-key", authorization.get())
            } finally {
                server.stop(0)
            }
        }
}
