package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ProviderFinishReason
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Verifies provider behavior that does not require a real Codex subscription. */
class CodexCliProviderTest {
    @Test
    fun `provider boundary preserves Codex item metadata`() {
        val response =
            ChatResponse(
                listOf(
                    Generation(
                        AssistantMessage("hello"),
                        ChatGenerationMetadata.builder().build(),
                    ),
                ),
                ChatResponseMetadata
                    .builder()
                    .model("gpt-test")
                    .keyValue("codexItemId", "item-7")
                    .build(),
            )

        val message = response.toCodexProviderMessage()

        assertEquals("hello", message.content)
        assertEquals("""{"codexItemId":"item-7"}""", message.metadata)
    }

    @Test
    fun `provider boundary maps Codex reasoning and completion fields`() {
        val response =
            ChatResponse(
                listOf(Generation(AssistantMessage("answer"), ChatGenerationMetadata.builder().finishReason("stop").build())),
                ChatResponseMetadata
                    .builder()
                    .model("gpt-test")
                    .keyValue("codexItemId", "item-8")
                    .keyValue("codexReasoning", "planning")
                    .build(),
            )

        val message = response.toCodexProviderMessage()
        val turn = response.toCodexProviderTurn("fallback")

        assertEquals("answer", message.content)
        assertEquals("planning", turn.reasoning)
        assertEquals(ProviderFinishReason.STOP, turn.finishReason)
        assertEquals("item-8", turn.metadata.responseId)
    }

    @Test
    fun `profileless operations fail explicitly`() =
        runBlocking {
            val provider = CodexCliProvider(mockk(), mockk(), mockk())

            assertFailsWith<IllegalStateException> { provider.chat(listOf(Message("user", "hello"))) }
            assertFailsWith<IllegalStateException> { provider.stream(listOf(Message("user", "hello"))) }
            assertFailsWith<IllegalStateException> { provider.vision(byteArrayOf(), "describe") }
            assertFailsWith<IllegalStateException> { provider.getModels() }
            assertEquals(emptyList(), provider.embeddings("text"))
            assertEquals(true, provider.isConnected())
            assertEquals(false, provider.checkConnection())
            assertEquals("model", provider.getModelDetails("model").model)
        }
}
