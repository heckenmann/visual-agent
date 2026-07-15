package de.heckenmann.visualagent.agent.openai

import de.heckenmann.visualagent.agent.ShowResponse
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.config.AppConfigBean
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OpenAiClientErrorTest {
    @Test
    fun `isConnected requires non blank api key`() {
        val config = AppConfigBean()
        config.openAiApiKey = ""
        config.openAiBaseUrl = "https://api.openai.com"
        val client =
            OpenAiClient(
                mockFactory(),
                ToolRegistry(
                    emptyList(),
                    ToolEventBus(),
                    config,
                ),
                config,
            )

        assertEquals(false, client.isConnected())
    }

    @Test
    fun `getModels throws when api key is blank for official endpoint`() =
        kotlinx.coroutines.test.runTest {
            val config = AppConfigBean()
            config.openAiApiKey = ""
            config.openAiBaseUrl = "https://api.openai.com"
            val client =
                OpenAiClient(
                    mockFactory(),
                    ToolRegistry(
                        emptyList(),
                        ToolEventBus(),
                    ),
                )

            val error = assertFailsWith<IllegalStateException> { client.getModels() }

            assertEquals("OpenAI API key is not configured", error.message)
        }

    @Test
    fun `getModelDetails returns profile family`() =
        kotlinx.coroutines.test.runTest {
            val client =
                OpenAiClient(
                    mockFactory(),
                    de.heckenmann.visualagent.agent.tools
                        .ToolRegistry(
                            emptyList(),
                            de.heckenmann.visualagent.agent.tools
                                .ToolEventBus(),
                            AppConfigBean(),
                        ),
                    AppConfigBean(),
                )
            val profile =
                de.heckenmann.visualagent.agent.provider.ProviderProfile(
                    id = "custom",
                    name = "Custom",
                    adapter = de.heckenmann.visualagent.agent.provider.ProviderAdapter.OPENAI_COMPATIBLE,
                    baseUrl = "http://localhost:8080",
                    apiKey = "",
                    defaultModel = "m",
                )

            val details: ShowResponse = client.getModelDetails(profile, "m")

            assertEquals("m", details.model)
            assertEquals("Custom", details.details?.family)
        }

    @Test
    fun `buildDetailedProviderError extracts response body`() {
        val client =
            OpenAiClient(
                mockFactory(),
                de.heckenmann.visualagent.agent.tools
                    .ToolRegistry(
                        emptyList(),
                        de.heckenmann.visualagent.agent.tools
                            .ToolEventBus(),
                    ),
            )
        val exception = ExceptionWithResponseBody("HTTP 500 Internal Server Error", "{\"error\":\"bad request\"}")

        val error = invokeBuildDetailedProviderError(client, exception)

        assertEquals("HTTP 500 Internal Server Error: {\"error\":\"bad request\"}", error.message)
        assertTrue(error.cause === exception)
    }

    @Test
    fun `extractDetailedErrorMessage uses fallback chain`() {
        val client =
            OpenAiClient(
                mockFactory(),
                de.heckenmann.visualagent.agent.tools
                    .ToolRegistry(
                        emptyList(),
                        de.heckenmann.visualagent.agent.tools
                            .ToolEventBus(),
                    ),
            )
        val nested = IllegalStateException("inner")
        val exception = RuntimeException("outer", nested)

        val error = invokeBuildDetailedProviderError(client, exception)

        assertEquals("inner", error.message)
    }

    private fun mockFactory(): OpenAiPromptFactory = io.mockk.mockk(relaxed = true)

    private fun invokeBuildDetailedProviderError(
        client: OpenAiClient,
        throwable: Throwable,
    ): IllegalStateException {
        val method = OpenAiClient::class.java.getDeclaredMethod("buildDetailedProviderError", Throwable::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(client, throwable) as IllegalStateException
    }

    private class ExceptionWithResponseBody(
        message: String,
        private val body: String,
    ) : Exception(message) {
        @Suppress("unused")
        fun getResponseBodyAsString(): String = body
    }
}
