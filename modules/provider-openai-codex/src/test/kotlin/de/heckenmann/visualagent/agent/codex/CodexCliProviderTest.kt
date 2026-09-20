package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ProviderFinishReason
import io.mockk.mockk
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
        assertTrue(turn.reasoningIsSummary)
        assertEquals(ProviderFinishReason.STOP, turn.finishReason)
        assertEquals("item-8", turn.metadata.responseId)
    }

    @Test
    fun `profileless operations fail explicitly`() =
        runBlocking {
            val provider = CodexCliProvider(mockk(), mockk(), mockk())

            assertFailsWith<IllegalStateException> { provider.chatReactive(listOf(Message("user", "hello"))).awaitSingle() }
            assertFailsWith<IllegalStateException> {
                provider.streamReactive(listOf(Message("user", "hello"))).collectList().awaitSingle()
            }
            assertFailsWith<IllegalStateException> { provider.visionReactive(byteArrayOf(), "describe").awaitSingle() }
            assertFailsWith<IllegalStateException> { provider.getModelsReactive().awaitSingle() }
            assertEquals(emptyList(), provider.embeddingsReactive("text").awaitSingle())
            assertEquals(true, provider.isConnected())
            assertEquals(false, provider.checkConnectionReactive().awaitSingle())
            assertEquals("model", provider.getModelDetailsReactive("model").awaitSingle().model)
        }
}
