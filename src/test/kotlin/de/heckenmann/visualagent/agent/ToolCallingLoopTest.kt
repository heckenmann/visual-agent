package de.heckenmann.visualagent.agent

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.ToolDefinition
import org.springframework.ai.tool.metadata.ToolMetadata
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.ai.chat.model.ChatResponse as SpringChatResponse

/**
 * Tests for [ToolCallingLoop].
 */
class ToolCallingLoopTest {
    @Test
    fun `run returns direct text response when model does not request tools`() {
        val chatModel = mockk<ChatModel>()
        val prompt = Prompt(listOf(UserMessage("hello")))
        every { chatModel.call(prompt) } returns springResponse("unit", "direct answer")

        val response = ToolCallingLoop().run(chatModel, prompt, emptyList())

        assertEquals("direct answer", response.message.content)
        assertEquals("unit", response.model)
    }

    @Test
    fun `run executes tool call and sends result back to model`() {
        val chatModel = mockk<ChatModel>()
        val tool = CountingTool()
        val prompt = Prompt(listOf(UserMessage("call the tool")))
        every { chatModel.call(any<Prompt>()) }
            .returnsMany(
                springToolResponse("unit", toolName = "count_tool", arguments = "{}", callId = "call-1"),
                springResponse("unit", "done after tool"),
            )

        val response = ToolCallingLoop().run(chatModel, prompt, listOf(tool))

        assertEquals("done after tool", response.message.content)
        assertEquals(1, tool.callCount)
    }

    @Test
    fun `run returns tool result directly when callback requests returnDirect`() {
        val chatModel = mockk<ChatModel>()
        val tool = DirectReturnTool()
        val prompt = Prompt(listOf(UserMessage("call direct tool")))
        every { chatModel.call(any<Prompt>()) } returns
            springToolResponse("unit", toolName = "direct_tool", arguments = "{}", callId = "call-1")

        val response = ToolCallingLoop().run(chatModel, prompt, listOf(tool))

        assertTrue(response.message.content.contains("direct result"))
    }

    @Test
    fun `run stops after max rounds when model keeps requesting tools`() {
        val chatModel = mockk<ChatModel>()
        val tool = CountingTool()
        val prompt = Prompt(listOf(UserMessage("loop")))
        every { chatModel.call(any<Prompt>()) } returns
            springToolResponse("unit", toolName = "count_tool", arguments = "{}", callId = "loop")

        val response = ToolCallingLoop(maxRounds = 3).run(chatModel, prompt, listOf(tool))

        assertEquals("", response.message.content)
        assertEquals(3, tool.callCount)
    }

    @Test
    fun `runStream emits final text after executing tool call from aggregated stream`() =
        runTest {
            val chatModel = mockk<ChatModel>()
            val tool = CountingTool()
            val prompt = Prompt(listOf(UserMessage("stream and tool")))
            every { chatModel.stream(any<Prompt>()) } returns
                reactor.core.publisher.Flux.just(
                    springToolResponse("unit", toolName = "count_tool", arguments = "{}", callId = "stream-1"),
                )
            every { chatModel.call(any<Prompt>()) } returns springResponse("unit", "final stream answer")

            val chunks = ToolCallingLoop().runStream(chatModel, prompt, listOf(tool)).toList()

            assertEquals(2, chunks.size)
            assertEquals("", chunks[0].message.content)
            assertEquals("final stream answer", chunks[1].message.content)
            assertEquals(1, tool.callCount)
        }

    @Test
    fun `runStream emits only model chunks when no tool call is requested`() =
        runTest {
            val chatModel = mockk<ChatModel>()
            val prompt = Prompt(listOf(UserMessage("just stream")))
            every { chatModel.stream(prompt) } returns
                reactor.core.publisher.Flux.just(
                    springResponse("unit", "chunk one"),
                    springResponse("unit", "chunk two"),
                )

            val chunks = ToolCallingLoop().runStream(chatModel, prompt, emptyList()).toList()

            assertEquals(2, chunks.size)
            assertEquals("chunk one", chunks[0].message.content)
            assertEquals("chunk two", chunks[1].message.content)
        }

    private fun springResponse(
        model: String,
        content: String,
    ): SpringChatResponse {
        val generation = Generation(AssistantMessage(content), ChatGenerationMetadata.builder().finishReason("stop").build())
        return SpringChatResponse(listOf(generation), ChatResponseMetadata.builder().model(model).build())
    }

    private fun springToolResponse(
        model: String,
        toolName: String,
        arguments: String,
        callId: String,
    ): SpringChatResponse {
        val toolCall = AssistantMessage.ToolCall(callId, "function", toolName, arguments)
        val assistantMessage =
            AssistantMessage
                .builder()
                .content("")
                .toolCalls(listOf(toolCall))
                .build()
        val generation = Generation(assistantMessage, ChatGenerationMetadata.builder().finishReason("tool_calls").build())
        return SpringChatResponse(listOf(generation), ChatResponseMetadata.builder().model(model).build())
    }

    private class CountingTool : ToolCallback {
        var callCount = 0

        override fun getToolDefinition(): ToolDefinition =
            ToolDefinition
                .builder()
                .name("count_tool")
                .description("Counts calls")
                .inputSchema("""{"type":"object"}""")
                .build()

        override fun call(toolInput: String): String {
            callCount++
            return """{"toolId":"count_tool","success":true,"content":"counted $callCount"}"""
        }
    }

    private class DirectReturnTool : ToolCallback {
        override fun getToolDefinition(): ToolDefinition =
            ToolDefinition
                .builder()
                .name("direct_tool")
                .description("Returns directly")
                .inputSchema("""{"type":"object"}""")
                .build()

        override fun getToolMetadata(): ToolMetadata = ToolMetadata.builder().returnDirect(true).build()

        override fun call(toolInput: String): String = "direct result"
    }
}
