package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.responses.OpenAiResponsesException
import org.springframework.ai.openai.responses.OpenAiResponsesMetadata
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import org.springframework.ai.chat.model.ChatResponse as SpringChatResponse

class ResponsesTerminalStatusTest {
    @Test
    fun `transport completion without response completed cannot report success`() {
        val model = mockk<ChatModel>()
        every { model.stream(any<Prompt>()) } returns Flux.just(response(AssistantMessage("partial"), null))

        StepVerifier
            .create(ReactiveResponsesToolLoop(mockk()).stream(model, prompt(), request()))
            .expectNextCount(1)
            .expectErrorMatches { it is OpenAiResponsesException && it.code == "missing_response_completed" }
            .verify()
    }

    @Test
    fun `explicit incomplete response is not treated as completed inference`() {
        val model = mockk<ChatModel>()
        every { model.stream(any<Prompt>()) } returns Flux.just(response(AssistantMessage("partial"), "incomplete"))

        StepVerifier
            .create(ReactiveResponsesToolLoop(mockk()).stream(model, prompt(), request()))
            .expectNextCount(1)
            .expectErrorMatches { it is OpenAiResponsesException && it.code == "response_incomplete" }
            .verify()
    }

    @Test
    fun `unfinished tool declarations cannot execute application side effects`() {
        val model = mockk<ChatModel>()
        val registry = mockk<ProviderToolCallbacks>()
        val assistant =
            AssistantMessage
                .builder()
                .toolCalls(listOf(AssistantMessage.ToolCall("call", "function", "tool_help", "{}")))
                .build()
        every { model.stream(any<Prompt>()) } returns Flux.just(response(assistant, null))

        StepVerifier
            .create(ReactiveResponsesToolLoop(registry).stream(model, prompt(), request()))
            .expectNextCount(1)
            .expectError(OpenAiResponsesException::class.java)
            .verify()

        verify(exactly = 0) { registry.recordAssistantToolTurn(any(), any()) }
    }

    private fun response(
        message: AssistantMessage,
        status: String?,
    ) = SpringChatResponse(
        listOf(Generation(message)),
        ChatResponseMetadata.builder().apply { status?.let { keyValue(OpenAiResponsesMetadata.STATUS, it) } }.build(),
    )

    private fun prompt() = Prompt(listOf(UserMessage("request")))

    private fun request() = ChatRequestContext(emptyList(), model = "selected")
}
