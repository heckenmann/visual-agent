package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.ToolResponseMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.ToolDefinition
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.ai.chat.model.ChatResponse as SpringChatResponse

/** Tests native batch adaptation in both provider loop entry paths with exact ordered history. */
class ToolCallingBatchLoopTest {
    @Test
    fun `streaming and nonstreaming loops share the native round contract`() {
        for (streaming in listOf(false, true)) {
            val captured = mutableListOf<Prompt>()
            val calls = mutableListOf<List<ProviderToolCall>>()
            val model = mockk<ChatModel>()
            val initial =
                SpringChatResponse(
                    listOf(
                        Generation(
                            AssistantMessage
                                .builder()
                                .content("Inspecting")
                                .toolCalls(
                                    listOf(
                                        AssistantMessage.ToolCall("first", "function", "read", "{}"),
                                        AssistantMessage.ToolCall("second", "function", "read", "{}"),
                                    ),
                                ).build(),
                        ),
                    ),
                )
            val finished = SpringChatResponse(listOf(Generation(AssistantMessage.builder().content("Done").build())))
            every { model.stream(any<Prompt>()) } returns Flux.just(initial)
            every { model.call(any<Prompt>()) } answers {
                val prompt = firstArg<Prompt>()
                captured += prompt
                if (!streaming && captured.size == 1) initial else finished
            }
            val tool =
                object : ToolCallback {
                    override fun getToolDefinition(): ToolDefinition =
                        ToolDefinition
                            .builder()
                            .name("read")
                            .description("Read")
                            .inputSchema("{}")
                            .build()

                    override fun call(functionInput: String): String = error("Default serial execution must not be used")
                }
            val boundary =
                object : ProviderToolCallbacks {
                    override fun functionCallbacks(
                        enabledTools: Set<ToolId>,
                        context: Map<String, Any>,
                    ): List<ToolCallback> = listOf(tool)

                    override fun recordAssistantToolTurn(
                        turn: ProviderTurnResponse,
                        context: Map<String, Any>,
                    ): String = "parent"

                    override fun executeToolCallRound(
                        toolCalls: List<ProviderToolCall>,
                        round: Int,
                        parentAssistantTurnId: String?,
                        enabledFunctionNames: Set<String>,
                        context: Map<String, Any>,
                    ): Mono<List<String>> {
                        calls += toolCalls
                        assertEquals("parent", parentAssistantTurnId)
                        assertEquals(setOf("read"), enabledFunctionNames)
                        return Mono.just(listOf("first result", "second result"))
                    }
                }
            val loop = ToolCallingLoop()
            val prompt = Prompt(listOf(UserMessage("Inspect both")))
            if (streaming) {
                loop.runStreamReactive(model, prompt, null, listOf(tool), boundary).collectList().block(Duration.ofSeconds(5))
            } else {
                loop.runReactive(model, prompt, null, listOf(tool), boundary).block(Duration.ofSeconds(5))
            }
            assertEquals(listOf("first", "second"), calls.single().map { it.id })
            val history =
                captured
                    .last()
                    .instructions
                    .filterIsInstance<ToolResponseMessage>()
                    .single()
                    .responses
            assertEquals(listOf("first", "second"), history.map { it.id() })
            assertEquals(listOf("first result", "second result"), history.map { it.responseData() })
            assertTrue(
                captured
                    .last()
                    .instructions
                    .filterIsInstance<AssistantMessage>()
                    .any { it.toolCalls.size == 2 },
            )
        }
    }
}
