package de.heckenmann.visualagent.agent.codexdirect

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ReactiveResponsesToolLoop
import de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode
import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.ToolResponseMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.messages.part.UnknownPart
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.responses.OpenAiResponsesChatOptions
import org.springframework.ai.openai.responses.OpenAiResponsesMetadata
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.ToolDefinition
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DirectResponsesToolContinuationTest {
    @Test
    fun `OAuth tool execution retains registry permissions and correlated results across streaming rounds`() {
        val registry = mockk<ProviderToolCallbacks>()
        val callback = mockk<ToolCallback>()
        every { callback.toolDefinition } returns
            ToolDefinition
                .builder()
                .name("workspace_file")
                .description("Files")
                .inputSchema("""{"type":"object"}""")
                .build()
        every { callback.toolMetadata.returnDirect() } returns false
        every { registry.recordAssistantToolTurn(any(), any()) } returns "parent"
        every { registry.bindToolCallRound(any(), any(), any()) } returns AutoCloseable {}
        every { registry.executeToolCallRound(any(), any(), any(), any(), any()) } returns Mono.just(listOf("managed files"))
        val delegate = mockk<ChatModel>()
        val received = mutableListOf<Prompt>()
        every { delegate.stream(capture(received)) } answers {
            val output =
                if (received.size == 1) {
                    AssistantMessage
                        .builder()
                        .toolCalls(listOf(AssistantMessage.ToolCall("call-1", "function", "workspace_file", "{}")))
                        .build()
                } else {
                    AssistantMessage("done")
                }
            Flux.just(
                ChatResponse(
                    listOf(Generation(output)),
                    ChatResponseMetadata.builder().keyValue(OpenAiResponsesMetadata.STATUS, "completed").build(),
                ),
            )
        }
        val request = ChatRequestContext(emptyList(), model = "selected")
        val options = directResponsesOptions(request, DirectProviderAuthMode.CHATGPT_OAUTH, listOf(callback))

        StepVerifier
            .create(
                ReactiveResponsesToolLoop(registry).stream(
                    DirectResponsesTransport(delegate, DirectProviderAuthMode.CHATGPT_OAUTH),
                    Prompt(listOf(UserMessage("list files")), options),
                    request,
                ),
            ).expectNextCount(1)
            .assertNext { assertEquals("done", it.message.content) }
            .verifyComplete()

        assertEquals(2, received.size)
        received.forEach { prompt ->
            assertTrue((prompt.options as OpenAiResponsesChatOptions).toolCallbacks.orEmpty().isEmpty())
            val nativeItems =
                prompt.instructions
                    .filterIsInstance<AssistantMessage>()
                    .flatMap { it.parts }
                    .filterIsInstance<UnknownPart>()
            assertEquals(1, nativeItems.count { it.kind() == "additional_tools" })
        }
        val result =
            received
                .last()
                .instructions
                .filterIsInstance<ToolResponseMessage>()
                .single()
                .responses
                .single()
        assertEquals("call-1", result.id())
        assertEquals("workspace_file", result.name())
        assertEquals("managed files", result.responseData())
        verify(exactly = 1) { registry.executeToolCallRound(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { callback.call(any<String>()) }
        verify(exactly = 0) { delegate.call(any<Prompt>()) }
    }
}
