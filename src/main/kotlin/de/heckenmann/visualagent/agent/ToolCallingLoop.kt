package de.heckenmann.visualagent.agent

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactive.asFlow
import mu.KotlinLogging
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.ToolCallingManager
import org.springframework.ai.model.tool.ToolExecutionResult
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver
import org.springframework.ai.chat.model.ChatResponse as SpringChatResponse

/**
 * Implements the request-side tool-calling loop for a Spring AI [ChatModel].
 *
 * Spring AI 2.0 no longer runs the tool loop inside the low-level [ChatModel]; it has
 * moved to the [org.springframework.ai.chat.client.advisor.ToolCallingAdvisor] in
 * [org.springframework.ai.chat.client.ChatClient]. Visual Agent calls the model directly,
 * so this helper runs the loop explicitly: detect tool calls, execute them through the
 * configured tools, feed the results back, and call the model again until a text
 * response is produced or the round limit is hit.
 *
 * @property maxRounds Maximum number of tool/model round-trips before giving up
 */
internal class ToolCallingLoop(
    private val maxRounds: Int = DEFAULT_MAX_ROUNDS,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Run a chat request with recursive tool calling.
     *
     * @param chatModel Model to call (already configured with the correct options)
     * @param initialPrompt First prompt including history, system instructions, and tools
     * @param toolCallbacks Tool callbacks that can fulfil the model's tool calls
     * @return Final response after all tool rounds completed
     */
    fun run(
        chatModel: ChatModel,
        initialPrompt: Prompt,
        toolCallbacks: List<ToolCallback>,
    ): ChatResponse {
        if (toolCallbacks.isEmpty()) {
            return chatModel.call(initialPrompt).toVisualAgentResponse()
        }
        val toolCallingManager = buildToolCallingManager(toolCallbacks)
        var prompt = initialPrompt
        var lastResponse: SpringChatResponse? = null

        repeat(maxRounds) { round ->
            logger.debug { "Tool calling round ${round + 1}/$maxRounds" }
            val response = chatModel.call(prompt)
            lastResponse = response

            if (!response.hasToolCalls()) {
                return response.toVisualAgentResponse()
            }

            val toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response)
            if (toolExecutionResult.returnDirect()) {
                return buildDirectResponse(response, toolExecutionResult)
            }

            prompt = appendToolConversationHistory(prompt, toolExecutionResult)
        }

        logger.warn { "Tool calling loop reached max rounds ($maxRounds); returning last response" }
        return lastResponse?.toVisualAgentResponse()
            ?: ChatResponse(
                model = "",
                message = Message(role = "assistant", content = ""),
                done = true,
            )
    }

    /**
     * Run a streaming chat request and finalize any tool calls after the stream ends.
     *
     * The stream itself only emits the model's text chunks. If the final aggregated
     * response contains tool calls, they are executed and a final non-streaming call is
     * made to obtain the user-facing answer. The returned flow emits the original chunks
     * followed by the final answer.
     *
     * @param chatModel Model to stream from
     * @param initialPrompt First prompt including history and tools
     * @param toolCallbacks Tool callbacks that can fulfil the model's tool calls
     * @return Flow of response chunks, including the final answer after tool execution
     */
    fun runStream(
        chatModel: ChatModel,
        initialPrompt: Prompt,
        toolCallbacks: List<ToolCallback>,
    ): Flow<ChatResponse> =
        flow {
            val springChunks = mutableListOf<SpringChatResponse>()
            val toolCallingManager = buildToolCallingManager(toolCallbacks)

            chatModel.stream(initialPrompt).asFlow().collect { springResponse ->
                springChunks += springResponse
                emit(springResponse.toVisualAgentResponse())
            }

            val aggregated = aggregateStreamingResponse(springChunks)
            if (aggregated?.hasToolCalls() != true) {
                return@flow
            }

            val toolExecutionResult = toolCallingManager.executeToolCalls(initialPrompt, aggregated)
            if (toolExecutionResult.returnDirect()) {
                val direct = buildDirectResponse(aggregated, toolExecutionResult)
                emit(direct)
                return@flow
            }

            val followUpPrompt = appendToolConversationHistory(initialPrompt, toolExecutionResult)
            val finalResponse = chatModel.call(followUpPrompt)
            emit(finalResponse.toVisualAgentResponse())
        }

    private fun buildToolCallingManager(toolCallbacks: List<ToolCallback>): ToolCallingManager =
        ToolCallingManager
            .builder()
            .toolCallbackResolver(StaticToolCallbackResolver(toolCallbacks))
            .build()

    private fun appendToolConversationHistory(
        prompt: Prompt,
        toolExecutionResult: ToolExecutionResult,
    ): Prompt {
        val messages = prompt.getInstructions().toMutableList()
        messages.addAll(toolExecutionResult.conversationHistory())
        return Prompt(messages, prompt.options)
    }

    private fun buildDirectResponse(
        originalResponse: SpringChatResponse,
        toolExecutionResult: ToolExecutionResult,
    ): ChatResponse {
        val directGenerations = ToolExecutionResult.buildGenerations(toolExecutionResult)
        val directContent = directGenerations.firstOrNull()?.let { it.output.text.orEmpty() }.orEmpty()
        return ChatResponse(
            model = originalResponse.metadata.model,
            message = Message(role = "assistant", content = directContent),
            done = true,
        )
    }

    private fun aggregateStreamingResponse(chunks: List<SpringChatResponse>): SpringChatResponse? {
        if (chunks.isEmpty()) return null
        val lastChunk = chunks.last()
        val content =
            chunks.joinToString("") {
                it.result
                    ?.output
                    ?.text
                    ?.orEmpty() ?: ""
            }
        val toolCalls =
            chunks.flatMap {
                it.result
                    ?.output
                    ?.toolCalls
                    .orEmpty()
            }
        val assistantMessage =
            AssistantMessage
                .builder()
                .content(content)
                .toolCalls(toolCalls)
                .build()
        val generation =
            Generation(
                assistantMessage,
                ChatGenerationMetadata
                    .builder()
                    .finishReason(lastChunk.result?.metadata?.finishReason ?: if (lastChunk.result != null) "stop" else null)
                    .build(),
            )
        return SpringChatResponse(listOf(generation))
    }

    private fun SpringChatResponse.toVisualAgentResponse(): ChatResponse {
        val generation = this.result
        val content = generation?.let { it.output.text.orEmpty() }.orEmpty()
        return ChatResponse(
            model = this.metadata.model,
            message = Message(role = "assistant", content = content),
            done = generation?.metadata?.finishReason != null,
            promptEvalCount = this.metadata.usage.promptTokens,
            evalCount = this.metadata.usage.completionTokens,
        )
    }

    companion object {
        private const val DEFAULT_MAX_ROUNDS = 5
    }
}
