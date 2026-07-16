package de.heckenmann.visualagent.agent.ollama

import com.sun.net.httpserver.HttpServer
import de.heckenmann.visualagent.config.AppConfigBean
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OllamaApiConfigurationTest {
    private val appConfig = AppConfigBean()

    @Test
    fun `api sends current key as bearer token`() {
        val authorization = AtomicReference<String?>()
        val server = modelServer(authorization)
        val config = appConfig
        val originalUrl = config.ollamaLocalUrl
        val originalKey = config.ollamaApiKey

        try {
            config.ollamaLocalUrl = "http://localhost:${server.address.port}"
            config.ollamaApiKey = "first-key"
            val api = createOllamaApi(config)

            api.listModels()
            assertEquals("Bearer first-key", authorization.get())

            config.ollamaApiKey = "replacement-key"
            api.listModels()
            assertEquals("Bearer replacement-key", authorization.get())
        } finally {
            config.ollamaLocalUrl = originalUrl
            config.ollamaApiKey = originalKey
            server.stop(0)
        }
    }

    @Test
    fun `api omits authorization when key is blank`() {
        val authorization = AtomicReference<String?>()
        val server = modelServer(authorization)
        val config = appConfig
        val originalUrl = config.ollamaLocalUrl
        val originalKey = config.ollamaApiKey

        try {
            config.ollamaLocalUrl = "http://localhost:${server.address.port}"
            config.ollamaApiKey = "  "

            createOllamaApi(config).listModels()

            assertNull(authorization.get())
        } finally {
            config.ollamaLocalUrl = originalUrl
            config.ollamaApiKey = originalKey
            server.stop(0)
        }
    }

    private fun modelServer(authorization: AtomicReference<String?>): HttpServer =
        HttpServer
            .create(InetSocketAddress("localhost", 0), 0)
            .apply {
                createContext("/api/tags") { exchange ->
                    authorization.set(exchange.requestHeaders.getFirst("Authorization"))
                    val body = """{"models":[]}""".toByteArray()
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                start()
            }
}
