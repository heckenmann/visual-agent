package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.ToolResponseMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.ToolCallingManager
import org.springframework.ai.tool.ToolCallback
import reactor.core.publisher.Sinks
import reactor.test.StepVerifier
import kotlin.test.assertEquals
import org.springframework.ai.chat.model.ChatResponse as SpringChatResponse

class ReactiveToolRoundTest {
    @Test
    fun `reactive registry result is deferred and propagated without waiting on subscription`() {
        val registry = mockk<ProviderToolCallbacks>()
        val results = Sinks.one<List<String>>()
        val callback = mockk<ToolCallback>(relaxed = true)
        every { callback.toolDefinition.name() } returns "tool_help"
        every { callback.toolMetadata.returnDirect() } returns false
        every { registry.executeToolCallRound(any(), any(), any(), any(), any()) } returns results.asMono()
        val response =
            SpringChatResponse(
                listOf(
                    Generation(
                        AssistantMessage
                            .builder()
                            .toolCalls(listOf(AssistantMessage.ToolCall("call-1", "function", "tool_help", "{}")))
                            .build(),
                    ),
                ),
            )
        val turn = ProviderTurnResponseMapper.fromSpring(response)
        val execution =
            executeCorrelatedToolsReactive(
                mockk<ToolCallingManager>(),
                Prompt(listOf(UserMessage("help"))),
                response,
                registry,
                turn,
                0,
                "turn-1",
                listOf(callback),
                emptyMap(),
            )

        StepVerifier
            .create(execution)
            .then { assertEquals(Sinks.EmitResult.OK, results.tryEmitValue(listOf("available tools"))) }
            .assertNext { result ->
                val message = result.conversationHistory().last() as ToolResponseMessage
                assertEquals("call-1", message.responses.single().id())
                assertEquals("available tools", message.responses.single().responseData())
            }.verifyComplete()
    }
}
