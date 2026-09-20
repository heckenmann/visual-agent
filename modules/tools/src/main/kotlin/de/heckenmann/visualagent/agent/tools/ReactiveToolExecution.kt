package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import mu.KotlinLogging
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CancellationException
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
            publishEvent(
                definition,
                ToolCallPhase.STARTED,
                functionInput,
                effectiveContext,
                ToolResult(definition.id.value, true, ""),
                startedAt,
                startedAt,
            )
            executeWithTimeout(
                tool,
                definition.id.value,
                functionInput,
                effectiveContext,
                deadlineNanos,
                cancellationToken,
            ).doOnSuccess { result ->
                publishEvent(
                    definition,
                    ToolCallPhase.FINISHED,
                    functionInput,
                    effectiveContext,
                    result ?: failure(definition.id.value, "TOOL_CANCELLED: Tool call completed without a result."),
                    startedAt,
                    Instant.now(),
                )
            }.doOnCancel {
                cancellationToken.cancel()
                publishEvent(
                    definition,
                    ToolCallPhase.FINISHED,
                    functionInput,
                    effectiveContext,
                    failure(definition.id.value, "TOOL_CANCELLED: Tool call was cancelled."),
                    startedAt,
                    Instant.now(),
                )
            }.doFinally { cancellationRegistration?.close() }
        }

    private fun executeWithTimeout(
        tool: VisualAgentTool,
        toolId: String,
        functionInput: String,
        effectiveContext: Map<String, Any>,
        deadlineNanos: Long,
        cancellationToken: ToolCancellationToken,
    ): Mono<ToolResult> =
        Mono.defer {
            val effectiveTimeoutNanos = remainingNanos(deadlineNanos)
            if (effectiveTimeoutNanos <= 0L) {
                Mono.just(timeoutFailure(toolId, 0L))
            } else {
                Mono
                    .create<ToolResult> { sink ->
                        val execution =
                            tool
                                .executeReactive(functionInput, effectiveContext)
                                .subscribeOn(Schedulers.boundedElastic())
                                .subscribe(
                                    sink::success,
                                    { error ->
                                        if (cancellationToken.isCancelled) {
                                            sink.success(failure(toolId, "TOOL_CANCELLED: Tool call was cancelled."))
                                        } else {
                                            sink.error(error)
                                        }
                                    },
                                )
                        val cancellationRegistration =
                            cancellationToken.onCancelled {
                                execution.dispose()
                                sink.success(failure(toolId, "TOOL_CANCELLED: Tool call was cancelled."))
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
                                Mono.just(failure(toolId, "TOOL_CANCELLED: Tool call was cancelled."))

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
    ) {
        toolEventBus.publish(
            ToolCallEvent(
                toolId = definition.id.value,
                functionName = definition.name,
                providerToolCallId = context["providerToolCallId"] as? String,
                requestId = context["requestId"] as? String,
                round = context["toolCallRound"] as? Int,
                sequence = context["toolCallSequence"] as? Int,
                phase = phase,
                inputJson = functionInput,
                context = context,
                result = result,
                startedAtUtc = startedAt,
                finishedAtUtc = finishedAt,
                durationMillis = Duration.between(startedAt, finishedAt).toMillis(),
            ),
        )
    }

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
