package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import mu.KotlinLogging
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.ToolCallingManager
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
    private val outputLimitUpdater: (ChatOptions, Int) -> ChatOptions = { options, limit ->
        options.mutate().maxTokens(limit).build()
    },
    private val responseSchema: ResponseSchema? = null,
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
        requestMetadata: Map<String, Any> = emptyMap(),
        onContextBudgeted: ((ContextBudgetStatus) -> Unit)? = null,
    ): ChatResponse {
        token?.throwIfCancelled()
        val budgetRequest =
            ChatRequestContext(
                messages = emptyList(),
                contextWindow = contextWindow,
                onContextBudgeted = onContextBudgeted,
                responseSchema = responseSchema,
            )
        if (toolCallbacks.isEmpty()) {
            return fitPrompt(budgetRequest, initialPrompt).let(chatModel::call).toVisualAgentResponse()
        }
        val boundPrompt = bindToolCallbacks(initialPrompt, toolCallbacks)
        val toolCallingManager = buildToolCallingManager()
        var prompt = boundPrompt
        var lastResponse: SpringChatResponse? = null

        repeat(maxRounds) { round ->
            token?.throwIfCancelled()
            logger.debug { "Tool calling round ${round + 1}/$maxRounds" }
            val boundedPrompt = fitPrompt(budgetRequest, prompt, toolCallbacks)
            val response = chatModel.call(boundedPrompt)
            lastResponse = response
            if (!response.hasToolCalls()) return response.toVisualAgentResponse(round = round)

            val turn = ProviderTurnResponseMapper.fromSpring(response, round = round)
            val parentTurnId = callCorrelation?.recordAssistantToolTurn(turn, requestMetadata)
            val toolExecutionResult =
                (callCorrelation?.bindToolCallRound(turn.toolCalls, round, parentTurnId) ?: AutoCloseable {}).use {
                    executeCorrelatedTools(
                        toolCallingManager,
                        boundedPrompt,
                        response,
                        callCorrelation,
                        turn,
                        round,
                        parentTurnId,
                        toolCallbacks,
                        requestMetadata + (token?.let { mapOf("cancellationToken" to it) } ?: emptyMap()),
                    )
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
        requestMetadata: Map<String, Any> = emptyMap(),
        onContextBudgeted: ((ContextBudgetStatus) -> Unit)? = null,
    ): Mono<ChatResponse> =
        Mono
            .fromCallable {
                runBlocking(
                    chatModel,
                    initialPrompt,
                    token,
                    toolCallbacks,
                    callCorrelation,
                    contextWindow,
                    requestMetadata,
                    onContextBudgeted,
                )
            }.subscribeOn(Schedulers.boundedElastic())

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
        requestMetadata: Map<String, Any> = emptyMap(),
        onContextBudgeted: ((ContextBudgetStatus) -> Unit)? = null,
    ): Flux<ChatResponse> =
        Flux.defer {
            token?.throwIfCancelled()
            val budgetRequest =
                ChatRequestContext(
                    messages = emptyList(),
                    contextWindow = contextWindow,
                    onContextBudgeted = onContextBudgeted,
                    responseSchema = responseSchema,
                )
            val boundedPrompt = fitPrompt(budgetRequest, initialPrompt, toolCallbacks)
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
                    indexed.t2.toVisualAgentResponse(
                        requestId = requestMetadata["requestId"]?.toString(),
                        round = 0,
                        sequence = indexed.t1.toInt(),
                        normalizeContent = false,
                    )
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
                                requestMetadata,
                                onContextBudgeted,
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
        requestMetadata: Map<String, Any>,
        onContextBudgeted: ((ContextBudgetStatus) -> Unit)?,
    ): ChatResponse? {
        if (aggregated?.hasToolCalls() != true) return null
        val toolCallingManager = buildToolCallingManager()
        val requestId = requestMetadata["requestId"]?.toString()
        val initialTurn = ProviderTurnResponseMapper.fromSpring(aggregated, requestId = requestId, round = 0)
        val parentTurnId = callCorrelation?.recordAssistantToolTurn(initialTurn, requestMetadata)
        val toolExecutionResult =
            (callCorrelation?.bindToolCallRound(initialTurn.toolCalls, 0, parentTurnId) ?: AutoCloseable {}).use {
                executeCorrelatedTools(
                    toolCallingManager,
                    initialPrompt,
                    aggregated,
                    callCorrelation,
                    initialTurn,
                    0,
                    parentTurnId,
                    toolCallbacks,
                    requestMetadata + (token?.let { mapOf("cancellationToken" to it) } ?: emptyMap()),
                )
            }
        val initialVisibleText =
            aggregated.result
                ?.output
                ?.text
                .orEmpty()
        if (toolExecutionResult.returnDirect()) {
            return StreamSectionBoundary.separateResponse(initialVisibleText, buildDirectResponse(aggregated, toolExecutionResult))
        }

        var prompt = appendToolConversationHistory(initialPrompt, toolExecutionResult)
        var lastFinalResponse: SpringChatResponse? = null
        val budgetRequest =
            ChatRequestContext(
                messages = emptyList(),
                contextWindow = contextWindow,
                onContextBudgeted = onContextBudgeted,
                responseSchema = responseSchema,
            )
        repeat(maxRounds) { followUpRoundIndex ->
            val round = followUpRoundIndex + 1
            token?.throwIfCancelled()
            logger.debug { "Stream tool follow-up round $round/$maxRounds" }
            val boundedPrompt = fitPrompt(budgetRequest, prompt, toolCallbacks)
            val finalResponse = chatModel.call(boundedPrompt)
            lastFinalResponse = finalResponse
            if (!finalResponse.hasToolCalls()) {
                return StreamSectionBoundary.separateResponse(
                    initialVisibleText,
                    finalResponse.toVisualAgentResponse(requestId = requestId, round = round),
                )
            }

            val turn = ProviderTurnResponseMapper.fromSpring(finalResponse, requestId = requestId, round = round)
            val nextParentTurnId = callCorrelation?.recordAssistantToolTurn(turn, requestMetadata)
            val nextToolResult =
                (callCorrelation?.bindToolCallRound(turn.toolCalls, round, nextParentTurnId) ?: AutoCloseable {}).use {
                    executeCorrelatedTools(
                        toolCallingManager,
                        boundedPrompt,
                        finalResponse,
                        callCorrelation,
                        turn,
                        round,
                        nextParentTurnId,
                        toolCallbacks,
                        requestMetadata + (token?.let { mapOf("cancellationToken" to it) } ?: emptyMap()),
                    )
                }
            if (nextToolResult.returnDirect()) {
                return StreamSectionBoundary.separateResponse(initialVisibleText, buildDirectResponse(finalResponse, nextToolResult))
            }
            prompt = appendToolConversationHistory(boundedPrompt, nextToolResult)
        }

        logger.warn { "Stream tool calling loop reached max rounds ($maxRounds); emitting last response" }
        return lastFinalResponse
            ?.toVisualAgentResponse(requestId = requestId, round = maxRounds)
            ?.let { StreamSectionBoundary.separateResponse(initialVisibleText, it) }
    }

    private fun buildToolCallingManager(): ToolCallingManager =
        ToolCallingManager
            .builder()
            .build()

    private fun fitPrompt(
        request: ChatRequestContext,
        prompt: Prompt,
        toolCallbacks: List<ToolCallback> = emptyList(),
    ): Prompt = contextBudgeter.fitPrompt(request, prompt, toolCallbacks, outputLimitUpdater)

    companion object {
        private const val DEFAULT_MAX_ROUNDS = 5
    }
}
