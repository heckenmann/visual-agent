package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
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
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
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
    private val contextBudgeter: RequestContextBudgeter = RequestContextBudgeter(),
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Executes Spring AI's synchronous ChatModel tool loop on boundedElastic.
     * Callers use [runReactive] rather than invoking this compatibility method directly.
     */
    private fun runBlocking(
        chatModel: ChatModel,
        initialPrompt: Prompt,
        token: CancellationToken?,
        toolCallbacks: List<ToolCallback>,
        callCorrelation: ProviderToolCallbacks? = null,
        contextWindow: ContextWindow = ContextWindow(),
    ): ChatResponse {
        token?.throwIfCancelled()
        val budgetRequest = ChatRequestContext(messages = emptyList(), contextWindow = contextWindow)
        if (toolCallbacks.isEmpty()) {
            return contextBudgeter.fitPrompt(budgetRequest, initialPrompt).let(chatModel::call).toVisualAgentResponse()
        }
        val boundPrompt = bindToolCallbacks(initialPrompt, toolCallbacks)
        val toolCallingManager = buildToolCallingManager()
        var prompt = boundPrompt
        var lastResponse: SpringChatResponse? = null

        repeat(maxRounds) { round ->
            token?.throwIfCancelled()
            logger.debug { "Tool calling round ${round + 1}/$maxRounds" }
            val boundedPrompt = contextBudgeter.fitPrompt(budgetRequest, prompt, toolCallbacks)
            val response = chatModel.call(boundedPrompt)
            lastResponse = response
            if (!response.hasToolCalls()) return response.toVisualAgentResponse(round = round)

            val turn = ProviderTurnResponseMapper.fromSpring(response, round = round)
            val toolExecutionResult =
                (callCorrelation?.bindToolCallRound(turn.toolCalls, round) ?: AutoCloseable {}).use {
                    toolCallingManager.executeToolCalls(boundedPrompt, response)
                }
            if (toolExecutionResult.returnDirect()) return buildDirectResponse(response, toolExecutionResult)
            prompt = appendToolConversationHistory(boundedPrompt, toolExecutionResult)
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
     * Runs the bounded tool-calling state machine through the server's reactive contract.
     *
     * Spring AI's non-streaming [ChatModel.call] method is blocking, so its work is isolated on
     * Reactor's shared bounded-elastic scheduler. Native Spring AI streaming remains a [Flux] in
     * [runStreamReactive].
     */
    fun runReactive(
        chatModel: ChatModel,
        initialPrompt: Prompt,
        token: CancellationToken?,
        toolCallbacks: List<ToolCallback>,
        callCorrelation: ProviderToolCallbacks? = null,
        contextWindow: ContextWindow = ContextWindow(),
    ): Mono<ChatResponse> =
        Mono
            .fromCallable { runBlocking(chatModel, initialPrompt, token, toolCallbacks, callCorrelation, contextWindow) }
            .subscribeOn(Schedulers.boundedElastic())

    /**
     * Runs Spring AI streaming without converting its native [Flux] to a coroutine [Flow].
     *
     * Chunks keep provider order. Once the initial stream completes with tool calls, the bounded
     * follow-up state machine runs on Reactor's blocking scheduler and contributes at most one
     * final response.
     */
    fun runStreamReactive(
        chatModel: ChatModel,
        initialPrompt: Prompt,
        token: CancellationToken?,
        toolCallbacks: List<ToolCallback>,
        callCorrelation: ProviderToolCallbacks? = null,
        contextWindow: ContextWindow = ContextWindow(),
    ): Flux<ChatResponse> =
        Flux.defer {
            token?.throwIfCancelled()
            val budgetRequest = ChatRequestContext(messages = emptyList(), contextWindow = contextWindow)
            val boundedPrompt = contextBudgeter.fitPrompt(budgetRequest, initialPrompt, toolCallbacks)
            if (toolCallbacks.isEmpty()) {
                return@defer chatModel.stream(boundedPrompt).map { springResponse ->
                    token?.throwIfCancelled()
                    springResponse.toVisualAgentResponse()
                }
            }
            val boundPrompt = bindToolCallbacks(boundedPrompt, toolCallbacks)
            val springChunks = mutableListOf<SpringChatResponse>()

            chatModel
                .stream(boundPrompt)
                .doOnNext { springChunks += it }
                .index()
                .map { indexed ->
                    token?.throwIfCancelled()
                    indexed.t2.toVisualAgentResponse(sequence = indexed.t1.toInt())
                }.concatWith(
                    Mono
                        .fromCallable {
                            finishStreamingToolCalls(
                                chatModel,
                                boundPrompt,
                                aggregateStreamingResponse(springChunks),
                                token,
                                callCorrelation,
                                toolCallbacks,
                                contextWindow,
                            )
                        }.subscribeOn(Schedulers.boundedElastic()),
                )
        }

    private fun finishStreamingToolCalls(
        chatModel: ChatModel,
        initialPrompt: Prompt,
        aggregated: SpringChatResponse?,
        token: CancellationToken?,
        callCorrelation: ProviderToolCallbacks?,
        toolCallbacks: List<ToolCallback>,
        contextWindow: ContextWindow,
    ): ChatResponse? {
        if (aggregated?.hasToolCalls() != true) return null
        val toolCallingManager = buildToolCallingManager()
        val initialTurn = ProviderTurnResponseMapper.fromSpring(aggregated, round = 0)
        val toolExecutionResult =
            (callCorrelation?.bindToolCallRound(initialTurn.toolCalls, 0) ?: AutoCloseable {}).use {
                toolCallingManager.executeToolCalls(initialPrompt, aggregated)
            }
        if (toolExecutionResult.returnDirect()) return buildDirectResponse(aggregated, toolExecutionResult)

        var prompt = appendToolConversationHistory(initialPrompt, toolExecutionResult)
        var lastFinalResponse: SpringChatResponse? = null
        val budgetRequest = ChatRequestContext(messages = emptyList(), contextWindow = contextWindow)
        repeat(maxRounds) { followUpRoundIndex ->
            val round = followUpRoundIndex + 1
            token?.throwIfCancelled()
            logger.debug { "Stream tool follow-up round $round/$maxRounds" }
            val boundedPrompt = contextBudgeter.fitPrompt(budgetRequest, prompt, toolCallbacks)
            val finalResponse = chatModel.call(boundedPrompt)
            lastFinalResponse = finalResponse
            if (!finalResponse.hasToolCalls()) return finalResponse.toVisualAgentResponse(round = round)

            val turn = ProviderTurnResponseMapper.fromSpring(finalResponse, round = round)
            val nextToolResult =
                (callCorrelation?.bindToolCallRound(turn.toolCalls, round) ?: AutoCloseable {}).use {
                    toolCallingManager.executeToolCalls(boundedPrompt, finalResponse)
                }
            if (nextToolResult.returnDirect()) return buildDirectResponse(finalResponse, nextToolResult)
            prompt = appendToolConversationHistory(boundedPrompt, nextToolResult)
        }

        logger.warn { "Stream tool calling loop reached max rounds ($maxRounds); emitting last response" }
        return lastFinalResponse?.toVisualAgentResponse(round = maxRounds)
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
    ): Prompt = Prompt(toolExecutionResult.conversationHistory(), prompt.options)

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
