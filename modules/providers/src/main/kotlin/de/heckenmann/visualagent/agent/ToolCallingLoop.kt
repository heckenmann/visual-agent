package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
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
import org.springframework.ai.model.tool.ToolCallingChatOptions
import org.springframework.ai.model.tool.ToolCallingManager
import org.springframework.ai.model.tool.ToolExecutionResult
import org.springframework.ai.tool.ToolCallback
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
     * @param token Optional cancellation token to honour between rounds
     * @param toolCallbacks Tool callbacks that can fulfil the model's tool calls
     * @return Final response after all tool rounds completed
     */
    fun run(
        chatModel: ChatModel,
        initialPrompt: Prompt,
        token: CancellationToken?,
        toolCallbacks: List<ToolCallback>,
        callCorrelation: ProviderToolCallbacks? = null,
    ): ChatResponse {
        token?.throwIfCancelled()
        if (toolCallbacks.isEmpty()) return chatModel.call(initialPrompt).toVisualAgentResponse()
        val boundPrompt = bindToolCallbacks(initialPrompt, toolCallbacks)
        val toolCallingManager = buildToolCallingManager()
        var prompt = boundPrompt
        var lastResponse: SpringChatResponse? = null

        repeat(maxRounds) { round ->
            token?.throwIfCancelled()
            logger.debug { "Tool calling round ${round + 1}/$maxRounds" }
            val response = chatModel.call(prompt)
            lastResponse = response

            if (!response.hasToolCalls()) {
                return response.toVisualAgentResponse(round = round)
            }

            val turn = ProviderTurnResponseMapper.fromSpring(response, round = round)
            val toolExecutionResult =
                (callCorrelation?.bindToolCallRound(turn.toolCalls, round) ?: AutoCloseable {}).use {
                    toolCallingManager.executeToolCalls(prompt, response)
                }
            if (toolExecutionResult.returnDirect()) {
                return buildDirectResponse(response, toolExecutionResult)
            }

            prompt = appendToolConversationHistory(prompt, toolExecutionResult)
        }

        logger.warn { "Tool calling loop reached max rounds ($maxRounds); returning last response" }
        return lastResponse?.toVisualAgentResponse(round = maxRounds - 1)
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
     * @param token Optional cancellation token to honour during streaming and follow-up
     * @param toolCallbacks Tool callbacks that can fulfil the model's tool calls
     * @return Flow of response chunks, including the final answer after tool execution
     */
    fun runStream(
        chatModel: ChatModel,
        initialPrompt: Prompt,
        token: CancellationToken?,
        toolCallbacks: List<ToolCallback>,
        callCorrelation: ProviderToolCallbacks? = null,
    ): Flow<ChatResponse> =
        flow {
            token?.throwIfCancelled()
            if (toolCallbacks.isEmpty()) {
                chatModel.stream(initialPrompt).asFlow().collect { springResponse ->
                    token?.throwIfCancelled()
                    emit(springResponse.toVisualAgentResponse())
                }
                return@flow
            }
            val boundPrompt = bindToolCallbacks(initialPrompt, toolCallbacks)
            val toolCallingManager = buildToolCallingManager()
            val springChunks = mutableListOf<SpringChatResponse>()

            chatModel.stream(boundPrompt).asFlow().collect { springResponse ->
                token?.throwIfCancelled()
                springChunks += springResponse
                emit(springResponse.toVisualAgentResponse(sequence = springChunks.lastIndex))
            }

            val aggregated = aggregateStreamingResponse(springChunks)
            if (aggregated?.hasToolCalls() != true) {
                return@flow
            }

            val initialTurn = ProviderTurnResponseMapper.fromSpring(aggregated, round = 0)
            val toolExecutionResult =
                (callCorrelation?.bindToolCallRound(initialTurn.toolCalls, 0) ?: AutoCloseable {}).use {
                    toolCallingManager.executeToolCalls(boundPrompt, aggregated)
                }
            if (toolExecutionResult.returnDirect()) {
                val direct = buildDirectResponse(aggregated, toolExecutionResult)
                emit(direct)
                return@flow
            }

            var prompt = appendToolConversationHistory(boundPrompt, toolExecutionResult)
            var lastFinalResponse: SpringChatResponse? = null
            repeat(maxRounds) { followUpRoundIndex ->
                val round = followUpRoundIndex + 1
                token?.throwIfCancelled()
                logger.debug { "Stream tool follow-up round $round/$maxRounds" }
                val finalResponse = chatModel.call(prompt)
                lastFinalResponse = finalResponse

                if (!finalResponse.hasToolCalls()) {
                    emit(finalResponse.toVisualAgentResponse(round = round))
                    return@flow
                }

                val turn = ProviderTurnResponseMapper.fromSpring(finalResponse, round = round)
                val nextToolResult =
                    (callCorrelation?.bindToolCallRound(turn.toolCalls, round) ?: AutoCloseable {}).use {
                        toolCallingManager.executeToolCalls(prompt, finalResponse)
                    }
                if (nextToolResult.returnDirect()) {
                    val direct = buildDirectResponse(finalResponse, nextToolResult)
                    emit(direct)
                    return@flow
                }
                prompt = appendToolConversationHistory(prompt, nextToolResult)
            }

            logger.warn { "Stream tool calling loop reached max rounds ($maxRounds); emitting last response" }
            lastFinalResponse?.let { emit(it.toVisualAgentResponse(round = maxRounds)) }
        }

    private fun buildToolCallingManager(): ToolCallingManager =
        ToolCallingManager
            .builder()
            .build()

    private fun bindToolCallbacks(
        prompt: Prompt,
        toolCallbacks: List<ToolCallback>,
    ): Prompt {
        val options = prompt.options
        val boundOptions =
            if (options is ToolCallingChatOptions) {
                options.mutate().toolCallbacks(toolCallbacks).build()
            } else {
                ToolCallingChatOptions.builder().toolCallbacks(toolCallbacks).build()
            }
        return Prompt(prompt.instructions, boundOptions)
    }

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

    private fun SpringChatResponse.toVisualAgentResponse(
        round: Int? = null,
        sequence: Int? = null,
    ): ChatResponse =
        ProviderTurnResponseMapper.toChatResponse(
            ProviderTurnResponseMapper.fromSpring(
                this,
                round = round,
                sequence = sequence,
            ),
        )

    companion object {
        private const val DEFAULT_MAX_ROUNDS = 5
    }
}
