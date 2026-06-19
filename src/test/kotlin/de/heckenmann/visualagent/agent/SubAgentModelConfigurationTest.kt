package de.heckenmann.visualagent.agent

import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SubAgentModelConfigurationTest {
    @Test
    fun `sub agent forwards its provider model and parameters`() =
        runTest {
            val provider = mockk<LLMProvider>()
            val request = slot<ChatRequestContext>()
            coEvery { provider.chat(capture(request)) } returns
                ChatResponse("qwen3.5", Message("assistant", "ok"), true)
            val agent =
                SubAgent(
                    id = "specialist",
                    name = "Specialist",
                    role = "Focused work",
                    config =
                        AgentConfig(
                            provider = "ollama",
                            model = "qwen3.5",
                            temperature = 0.15,
                            topP = 0.75,
                            maxTokens = 4096,
                        ),
                )

            agent.chat(listOf(Message("user", "work")), provider)

            assertEquals("ollama", request.captured.provider)
            assertEquals("qwen3.5", request.captured.model)
            assertEquals(ModelParameters(0.15, 0.75, 4096), request.captured.parameters)
        }

    @Test
    fun `agent model configuration survives persistence serialization`() {
        val config =
            AgentConfig(
                provider = "openai",
                model = "gpt-agent",
                temperature = 0.3,
                topP = 0.85,
                maxTokens = 2048,
            )

        val restored = Json.decodeFromString<AgentConfig>(Json.encodeToString(config))

        assertEquals(config, restored)
    }
}
