package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.responses.OpenAiResponsesChatOptions
import org.springframework.ai.openai.responses.OpenAiResponsesMetadata
import org.springframework.ai.tool.ToolCallback
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.test.StepVerifier
import java.util.concurrent.CancellationException
import kotlin.test.assertEquals
import org.springframework.ai.chat.model.ChatResponse as SpringChatResponse

class ReactiveResponsesToolLoopTest {
    @Test
    fun `computed output budget is applied unless the selected route is OAuth`() {
        DirectProviderAuthMode.entries.forEach { mode ->
            val model = mockk<ChatModel>()
            val captured = slot<Prompt>()
            every { model.stream(capture(captured)) } returns Flux.just(text("done"))
            val profile =
                ProviderProfile(
                    "profile",
                    "Profile",
                    ProviderAdapter.CODEX_DIRECT,
                    "https://api.openai.com",
                    directAuthMode = mode,
                )
            val request = request().copy(providerProfile = profile, contextWindow = ContextWindow(configuredLimit = 4096))
            val options = OpenAiResponsesChatOptions.builder().model("selected").build()
            StepVerifier
                .create(
                    ReactiveResponsesToolLoop(mockk()).stream(model, Prompt(listOf(UserMessage("question")), options), request),
                ).expectNextCount(2)
                .verifyComplete()
            val limit = (captured.captured.options as OpenAiResponsesChatOptions).maxOutputTokens
            assertEquals(if (mode == DirectProviderAuthMode.API_KEY) 1024 else null, limit)
        }
    }

    @Test
    fun `text reaches subscriber before the model stream completes`() {
        val model = mockk<ChatModel>()
        val chunks = Sinks.many().unicast().onBackpressureBuffer<SpringChatResponse>()
        every { model.stream(any<Prompt>()) } returns chunks.asFlux()
        val loop = ReactiveResponsesToolLoop(mockk<ProviderToolCallbacks>())

        StepVerifier
            .create(loop.stream(model, prompt(), request()))
            .then { chunks.tryEmitNext(text("first", false)) }
            .assertNext { assertEquals("first", it.message.content) }
            .then { chunks.tryEmitNext(text("second", false)) }
            .assertNext { assertEquals("second", it.message.content) }
            .then { chunks.tryEmitNext(text("")) }
            .expectNextCount(1)
            .then { chunks.tryEmitComplete() }
            .verifyComplete()

        verify(exactly = 0) { model.call(any<Prompt>()) }
    }

    @Test
    fun `tool continuation uses streaming instead of synchronous inference`() {
        val model = mockk<ChatModel>()
        val registry = mockk<ProviderToolCallbacks>()
        val callback = mockk<ToolCallback>(relaxed = true)
        every { callback.toolDefinition.name() } returns "tool_help"
        every { callback.toolMetadata.returnDirect() } returns false
        every { registry.recordAssistantToolTurn(any(), any()) } returns "turn-1"
        every { registry.bindToolCallRound(any(), any(), any()) } returns AutoCloseable {}
        every { registry.executeToolCallRound(any(), any(), any(), any(), any()) } returns Mono.just(listOf("tools"))
        val toolResponse =
            SpringChatResponse(
                listOf(
                    Generation(
                        AssistantMessage
                            .builder()
                            .toolCalls(listOf(AssistantMessage.ToolCall("call-1", "function", "tool_help", "{}")))
                            .build(),
                    ),
                ),
                ChatResponseMetadata.builder().keyValue(OpenAiResponsesMetadata.STATUS, "completed").build(),
            )
        every { model.stream(any<Prompt>()) } returnsMany listOf(Flux.just(toolResponse), Flux.just(text("done")))
        val options =
            OpenAiResponsesChatOptions
                .builder()
                .model("selected")
                .toolCallbacks(listOf(callback))
                .build()

        StepVerifier
            .create(ReactiveResponsesToolLoop(registry).stream(model, Prompt(listOf(UserMessage("help")), options), request()))
            .expectNextCount(1)
            .assertNext { assertEquals("done", it.message.content) }
            .verifyComplete()

        verify(exactly = 2) { model.stream(any<Prompt>()) }
        verify(exactly = 0) { model.call(any<Prompt>()) }
    }

    @Test
    fun `cancellation disposes a stalled model stream without another chunk`() {
        val model = mockk<ChatModel>()
        val token = CancellationToken()
        every { model.stream(any<Prompt>()) } returns Flux.never()

        StepVerifier
            .create(ReactiveResponsesToolLoop(mockk()).stream(model, prompt(), request().copy(cancellationToken = token)))
            .then(token::cancel)
            .expectError(CancellationException::class.java)
            .verify()
    }

    private fun text(
        value: String,
        completed: Boolean = true,
    ) = SpringChatResponse(
        listOf(Generation(AssistantMessage(value))),
        if (completed) {
            ChatResponseMetadata.builder().keyValue(OpenAiResponsesMetadata.STATUS, "completed").build()
        } else {
            ChatResponseMetadata.builder().build()
        },
    )

    private fun prompt() = Prompt(listOf(UserMessage("help")))

    private fun request() = ChatRequestContext(emptyList(), model = "selected")
}
