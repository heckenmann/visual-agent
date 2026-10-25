package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.model.ToolLiveState
import mu.KotlinLogging
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Executes one tool invocation through the server's Reactor boundary.
 *
 * The component owns lifecycle events, bounded-elastic scheduling, timeout enforcement, and
 * cancellation propagation. It is internal because [ToolRegistry] remains the public tool API.
 */
internal class ReactiveToolExecution(
    private val toolEventBus: ToolEventBus,
) {
    private val logger = KotlinLogging.logger {}
    private val activeLiveCalls = ConcurrentHashMap<String, ActiveLiveToolCall>()

    /** Reads invocation-owned state once when a presentation subscribes after tool startup. */
    fun liveSnapshots(): List<ToolCallEvent> = activeLiveCalls.values.map { it.snapshot() }

    /** Cancels only an active execution belonging to the supplied conversation request. */
    fun cancelLive(
        executionId: String,
        requestId: String?,
    ): Boolean {
        val call = activeLiveCalls[executionId] ?: return false
        if (call.started.requestId != requestId) return false
        call.token.cancel()
        return true
    }

    /** Execute a prepared tool invocation and emit its structured result. */
    fun execute(
        tool: VisualAgentTool,
        definition: ToolDefinition,
        functionInput: String,
        context: Map<String, Any>,
        options: ToolExecutionOptions,
        deadlineNanos: Long,
        startedAt: Instant,
    ): Mono<ToolResult> =
        Mono.defer {
            val liveSession =
                if (tool is LiveVisualAgentTool) {
                    ToolLiveSession { state ->
                        publishEvent(
                            definition,
                            ToolCallPhase.UPDATED,
                            functionInput,
                            context,
                            ToolResult(definition.id.value, true, ""),
                            startedAt,
                            Instant.now(),
                            state,
                        )
                    }
                } else {
                    null
                }
            val cancellationToken = ToolCancellationToken()
            val cancellationRegistration =
                (context["toolCancellationRegistrar"] as? ToolCancellationRegistrar)?.register(cancellationToken::cancel)
            val effectiveContext =
                context +
                    mapOf(
                        "toolTimeoutSeconds" to options.timeoutSeconds,
                        "toolDeadlineNanos" to deadlineNanos,
                        "toolCancellationToken" to cancellationToken,
                    ) +
                    if (options.async) mapOf("async" to true) else emptyMap()
            val startedEvent =
                createEvent(
                    definition,
                    ToolCallPhase.STARTED,
                    functionInput,
                    effectiveContext,
                    ToolResult(definition.id.value, true, ""),
                    startedAt,
                    startedAt,
                    executionId = liveSession?.executionId,
                )
            liveSession?.let { activeLiveCalls[it.executionId] = ActiveLiveToolCall(cancellationToken, startedEvent, it) }
            toolEventBus.publish(startedEvent)
            liveSession?.start()
            executeWithTimeout(
                tool,
                definition.id.value,
                functionInput,
                effectiveContext,
                deadlineNanos,
                cancellationToken,
                liveSession,
            ).doOnSuccess { result ->
                liveSession?.let { activeLiveCalls.remove(it.executionId) }
                publishEvent(
                    definition,
                    ToolCallPhase.FINISHED,
                    functionInput,
                    effectiveContext,
                    result ?: failure(definition.id.value, "TOOL_CANCELLED: Tool call completed without a result."),
                    startedAt,
                    Instant.now(),
                    liveSession?.finish(liveTerminalStatus(result)),
                    executionId = liveSession?.executionId,
                )
            }.doOnCancel {
                liveSession?.let { activeLiveCalls.remove(it.executionId) }
                cancellationToken.cancel()
                publishEvent(
                    definition,
                    ToolCallPhase.FINISHED,
                    functionInput,
                    effectiveContext,
                    cancellationFailure(definition.id.value, deadlineNanos),
                    startedAt,
                    Instant.now(),
                    liveSession?.finish("Cancelled"),
                    executionId = liveSession?.executionId,
                )
            }.doFinally {
                liveSession?.close()
                liveSession?.let { activeLiveCalls.remove(it.executionId) }
                cancellationRegistration?.close()
            }
        }

    private fun executeWithTimeout(
        tool: VisualAgentTool,
        toolId: String,
        functionInput: String,
        effectiveContext: Map<String, Any>,
        deadlineNanos: Long,
        cancellationToken: ToolCancellationToken,
        liveSession: ToolLiveSession?,
    ): Mono<ToolResult> =
        Mono.defer {
            val effectiveTimeoutNanos = remainingNanos(deadlineNanos)
            if (effectiveTimeoutNanos <= 0L) {
                Mono.just(timeoutFailure(toolId, 0L))
            } else {
                Mono
                    .create<ToolResult> { sink ->
                        val execution =
                            Mono
                                .defer {
                                    (effectiveContext["taskToolAccess"] as? TaskToolAccess)?.requireAllowed(
                                        toolId,
                                        parseObject(functionInput),
                                    )
                                    if (tool is LiveVisualAgentTool && liveSession != null) {
                                        tool.executeLiveReactive(functionInput, effectiveContext, liveSession)
                                    } else {
                                        tool.executeReactive(functionInput, effectiveContext)
                                    }
                                }.subscribeOn(Schedulers.boundedElastic())
                                .subscribe(
                                    sink::success,
                                    { error ->
                                        if (cancellationToken.isCancelled) {
                                            sink.success(cancellationFailure(toolId, deadlineNanos))
                                        } else {
                                            sink.error(error)
                                        }
                                    },
                                )
                        val cancellationRegistration =
                            cancellationToken.onCancelled {
                                execution.dispose()
                                sink.success(cancellationFailure(toolId, deadlineNanos))
                            }
                        sink.onCancel {
                            execution.dispose()
                        }
                        sink.onDispose(cancellationRegistration::close)
                    }.timeout(
                        Duration.ofNanos(effectiveTimeoutNanos),
                        Mono.fromSupplier {
                            cancellationToken.cancel()
                            timeoutFailure(toolId, effectiveTimeoutNanos)
                        },
                    ).onErrorResume { error ->
                        when {
                            cancellationToken.isCancelled || error is CancellationException ->
                                Mono.just(cancellationFailure(toolId, deadlineNanos))

                            else -> {
                                val safeError = ToolResultNormalization.executionError(error)
                                logger.warn { "Tool execution failed for toolId=$toolId code=${safeError.code}" }
                                Mono.just(failure(toolId, ToolResultNormalization.legacyError(safeError)))
                            }
                        }
                    }
            }
        }

    private fun publishEvent(
        definition: ToolDefinition,
        phase: ToolCallPhase,
        functionInput: String,
        context: Map<String, Any>,
        result: ToolResult,
        startedAt: Instant,
        finishedAt: Instant,
        liveState: ToolLiveState? = null,
        executionId: String? = liveState?.executionId,
    ) {
        toolEventBus.publish(createEvent(definition, phase, functionInput, context, result, startedAt, finishedAt, liveState, executionId))
    }

    private fun createEvent(
        definition: ToolDefinition,
        phase: ToolCallPhase,
        functionInput: String,
        context: Map<String, Any>,
        result: ToolResult,
        startedAt: Instant,
        finishedAt: Instant,
        liveState: ToolLiveState? = null,
        executionId: String? = liveState?.executionId,
    ): ToolCallEvent =
        ToolCallEvent(
            toolId = definition.id.value,
            functionName = definition.name,
            providerToolCallId = context["providerToolCallId"] as? String,
            requestId = context["requestId"] as? String,
            round = context["toolCallRound"] as? Int,
            sequence = context["toolCallSequence"] as? Int,
            parentAssistantTurnId = context["parentAssistantTurnId"] as? String,
            phase = phase,
            inputJson = functionInput,
            context = context,
            result = result,
            startedAtUtc = startedAt,
            finishedAtUtc = finishedAt,
            durationMillis = Duration.between(startedAt, finishedAt).toMillis(),
            liveState = liveState,
            executionId = executionId,
        )

    // Deadline expiry must retain its timeout code even when cancellation wins the timer race.
    private fun cancellationFailure(
        toolId: String,
        deadlineNanos: Long,
    ): ToolResult =
        failure(
            toolId,
            if (remainingNanos(deadlineNanos) == 0L) {
                "TOOL_TIMEOUT: Tool call deadline expired."
            } else {
                "TOOL_CANCELLED: Tool call was cancelled."
            },
        )

    private fun remainingNanos(deadlineNanos: Long): Long = (deadlineNanos - System.nanoTime()).coerceAtLeast(0L)

    private fun timeoutFailure(
        toolId: String,
        effectiveTimeoutNanos: Long,
    ): ToolResult =
        failure(
            toolId,
            "TOOL_TIMEOUT: Tool call exceeded its effective timeout of ${formatTimeout(effectiveTimeoutNanos)}. " +
                "Retry with a larger timeoutSeconds value up to $MAX_TOOL_TIMEOUT_SECONDS when useful.",
        )

    private fun formatTimeout(timeoutNanos: Long): String =
        if (timeoutNanos < TimeUnit.SECONDS.toNanos(1)) "less than 1s" else "${TimeUnit.NANOSECONDS.toSeconds(timeoutNanos)}s"
}
