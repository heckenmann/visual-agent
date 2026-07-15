package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.ollama.api.OllamaApi
import java.time.Instant
import kotlin.test.assertNull

/**
 * Tests that verify the no-tools code path in [OllamaClient] bypasses the
 * Spring AI chat model and calls [OllamaApi] directly so the serialised
 * request body does not contain a `tools` field.
 */
class OllamaClientToollessRequestTest {
    @Test
    fun `chat goes through OllamaApi and produces request without tools field when no tools are enabled`() =
        runTest {
            val chatModel = mockk<ChatModel>()
            val ollamaApi = mockk<OllamaApi>()
            val requestSlot = slot<OllamaApi.ChatRequest>()
            every { ollamaApi.chat(capture(requestSlot)) } returns
                OllamaApi.ChatResponse(
                    "m",
                    Instant.now(),
                    OllamaApi.Message(OllamaApi.Message.Role.ASSISTANT, "hi", null, null, null, null),
                    null,
                    true,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                )
            val registry = ToolRegistry(emptyList(), ToolEventBus())
            val client = createClient(chatModel, ollamaApi, registry)

            client.chat(
                ChatRequestContext(
                    messages = listOf(Message("user", "hello")),
                    model = "m",
                    enabledTools = emptySet(),
                    modelCapabilities = setOf("tools"),
                ),
            )

            assertNull(
                requestSlot.captured.tools(),
                "Expected no tools in OllamaApi request, but got ${requestSlot.captured.tools()}",
            )
            verify(exactly = 0) { chatModel.call(any<Prompt>()) }
            verify(exactly = 1) { ollamaApi.chat(any()) }
        }
}
