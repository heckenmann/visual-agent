package de.heckenmann.visualagent.agent

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.reactor.awaitSingle
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
import reactor.core.publisher.Flux
import kotlin.test.assertEquals
import org.springframework.ai.chat.model.ChatResponse as SpringChatResponse

class ToolCallingLoopStreamingBoundaryTest {
    @Test
    fun `runStream separates visible initial prose from final tool response`() =
        runTest {
            val chatModel = mockk<ChatModel>()
            val prompt = Prompt(listOf(UserMessage("stream and tool")))
            every { chatModel.stream(any<Prompt>()) } returns
                Flux.just(toolResponse("count_tool", "Searching now."))
            every { chatModel.call(any<Prompt>()) } returns response("Here is the result.")

            val chunks = ToolCallingLoop().runStreamReactive(chatModel, prompt, null, listOf(CountingTool())).collectList().awaitSingle()

            assertEquals("Searching now.", chunks.first().message.content)
            assertEquals("\n\nHere is the result.", chunks.last().message.content)
            assertEquals(chunks.last().message.content, chunks.last().providerTurn?.content)
        }

    @Test
    fun `runStream preserves existing whitespace and empty final responses`() =
        runTest {
            val chatModel = mockk<ChatModel>()
            val prompt = Prompt(listOf(UserMessage("stream and tool")))
            every { chatModel.stream(any<Prompt>()) } returns Flux.just(toolResponse("count_tool", "Searching now."))
            every { chatModel.call(any<Prompt>()) }.returnsMany(response(" Already separated."), response(""))
            val loop = ToolCallingLoop()

            val whitespace = loop.runStreamReactive(chatModel, prompt, null, listOf(CountingTool())).collectList().awaitSingle()
            val empty = loop.runStreamReactive(chatModel, prompt, null, listOf(CountingTool())).collectList().awaitSingle()

            assertEquals(" Already separated.", whitespace.last().message.content)
            assertEquals("", empty.last().message.content)
        }

    @Test
    fun `runStream separates initial prose from a direct tool response`() =
        runTest {
            val chatModel = mockk<ChatModel>()
            val prompt = Prompt(listOf(UserMessage("stream and direct tool")))
            every { chatModel.stream(any<Prompt>()) } returns Flux.just(toolResponse("direct_tool", "Looking it up."))

            val chunks =
                ToolCallingLoop()
                    .runStreamReactive(
                        chatModel,
                        prompt,
                        null,
                        listOf(DirectReturnTool()),
                    ).collectList()
                    .awaitSingle()

            assertEquals("Looking it up.", chunks.first().message.content)
            assertEquals("\n\ndirect result", chunks.last().message.content)
        }

    private fun response(content: String): SpringChatResponse {
        val generation = Generation(AssistantMessage(content), ChatGenerationMetadata.builder().finishReason("stop").build())
        return SpringChatResponse(listOf(generation), ChatResponseMetadata.builder().model("unit").build())
    }

    private fun toolResponse(
        name: String,
        content: String,
    ): SpringChatResponse {
        val call = AssistantMessage.ToolCall("stream-1", "function", name, "{}")
        val message =
            AssistantMessage
                .builder()
                .content(content)
                .toolCalls(listOf(call))
                .build()
        val generation = Generation(message, ChatGenerationMetadata.builder().finishReason("tool_calls").build())
        return SpringChatResponse(listOf(generation), ChatResponseMetadata.builder().model("unit").build())
    }

    private class CountingTool : ToolCallback {
        override fun getToolDefinition(): ToolDefinition = definition("count_tool")

        override fun call(toolInput: String): String = "counted"
    }

    private class DirectReturnTool : ToolCallback {
        override fun getToolDefinition(): ToolDefinition = definition("direct_tool")

        override fun getToolMetadata(): ToolMetadata = ToolMetadata.builder().returnDirect(true).build()

        override fun call(toolInput: String): String = "direct result"
    }

    private companion object {
        fun definition(name: String): ToolDefinition =
            ToolDefinition
                .builder()
                .name(name)
                .description(name)
                .inputSchema("""{"type":"object"}""")
                .build()
    }
}
