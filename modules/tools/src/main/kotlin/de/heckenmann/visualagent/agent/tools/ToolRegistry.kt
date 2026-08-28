package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Provider-neutral registry and execution boundary for model-callable tools.
 *
 * Use cases: UC-0000019, UC-0000020, UC-0000042, UC-0000043, UC-0000044.
 */
class ToolRegistry(
    tools: List<VisualAgentTool>,
    private val toolEventBus: ToolEventBus,
    private val defaultTimeoutSeconds: () -> Int = { DEFAULT_TOOL_TIMEOUT_SECONDS },
) : AutoCloseable {
    private val toolsById = tools.associateBy { it.definition.id }
    private val executor = Executors.newCachedThreadPool()

    /**
     * Return all registered application tool IDs.
     *
     * @return Tool IDs known by the registry
     * @see docs/usecases/uc_0000019_configure_agent_tools.md
     */
    fun allToolIds(): Set<ToolId> = toolsById.keys

    /**
     * Return all registered tool definitions.
     *
     * @return Tool definitions known by the registry
     * @see docs/usecases/uc_0000019_configure_agent_tools.md
     */
    fun toolDefinitions(): List<ToolDefinition> =
        toolsById.values
            .map(::definition)
            .sortedBy { it.id.value }

    /** Returns the provider-visible definition including common runtime parameters. */
    fun definition(tool: VisualAgentTool): ToolDefinition = tool.definition.withRuntimeParameters()

    /** Returns the current provider-neutral runtime guidance for model instructions. */
    fun runtimeGuidance(): String = toolTimeoutGuidance(defaultTimeoutSeconds())

    /**
     * Resolve registered tools by ID.
     *
     * @param enabledTools Tool IDs requested for a model call
     * @return Matching registered tools in deterministic order
     * @see docs/usecases/uc_0000020_execute_tool_call.md
     */
    fun resolve(enabledTools: Set<ToolId>): List<VisualAgentTool> = enabledTools.mapNotNull(toolsById::get).sortedBy { it.definition.name }

    /**
     * Executes one registered tool with lifecycle events and timeout handling.
     *
     * @param tool resolved tool
     * @param functionInput JSON arguments from the provider
     * @param context request-scoped metadata
     * @return serialized structured result
     * @see docs/usecases/uc_0000020_execute_tool_call.md
     */
    fun execute(
        tool: VisualAgentTool,
        functionInput: String,
        context: Map<String, Any>,
    ): String {
        val definition = tool.definition
        val inputObject = parseObject(functionInput)
        val startedAt = Instant.now()
        val options =
            runCatching { runtimeOptions(inputObject, defaultTimeoutSeconds()) }
                .getOrElse { error ->
                    return completeImmediately(
                        definition,
                        functionInput,
                        context + mapOf("toolTimeoutSeconds" to defaultTimeoutSeconds()),
                        startedAt,
                        failure(
                            definition.id.value,
                            "TOOL_ARGUMENTS: ${error.message ?: "Invalid tool runtime arguments."}",
                        ),
                    )
                }
        val deadlineNanos = deadlineNanos(context, options.timeoutSeconds)
        if (remainingNanos(deadlineNanos) <= 0L) {
            return completeImmediately(
                definition,
                functionInput,
                context + mapOf("toolTimeoutSeconds" to options.timeoutSeconds),
                startedAt,
                timeoutFailure(definition.id.value, 0L),
            )
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
                )
        publishEvent(
            definition,
            ToolCallPhase.STARTED,
            functionInput,
            effectiveContext,
            ToolResult(definition.id.value, true, ""),
            startedAt,
            startedAt,
        )
        if (options.async) {
            scheduleAsyncExecution(
                tool = tool,
                definition = definition,
                functionInput = functionInput,
                effectiveContext = effectiveContext,
                deadlineNanos = deadlineNanos,
                cancellationToken = cancellationToken,
                cancellationRegistration = cancellationRegistration,
                startedAt = startedAt,
            )
            val accepted =
                success(
                    definition.id.value,
                    "scheduled async tool call (timeout=${options.timeoutSeconds}s)",
                )
            return Json.encodeToString(accepted)
        }
        val result =
            try {
                executeWithTimeout(tool, definition.id.value, functionInput, effectiveContext, deadlineNanos, cancellationToken)
            } finally {
                cancellationRegistration?.close()
            }
        val finishedAt = Instant.now()
        publishEvent(definition, ToolCallPhase.FINISHED, functionInput, effectiveContext, result, startedAt, finishedAt)
        return Json.encodeToString(result)
    }

    override fun close() {
        executor.shutdownNow()
    }

    private fun scheduleAsyncExecution(
        tool: VisualAgentTool,
        definition: ToolDefinition,
        functionInput: String,
        effectiveContext: Map<String, Any>,
        deadlineNanos: Long,
        cancellationToken: ToolCancellationToken,
        cancellationRegistration: AutoCloseable?,
        startedAt: Instant,
    ) {
        executor.submit {
            try {
                val result =
                    executeWithTimeout(
                        tool,
                        definition.id.value,
                        functionInput,
                        effectiveContext,
                        deadlineNanos,
                        cancellationToken,
                    )
                val finishedAt = Instant.now()
                toolEventBus.publish(
                    ToolCallEvent(
                        toolId = definition.id.value,
                        functionName = definition.name,
                        providerToolCallId = effectiveContext["providerToolCallId"] as? String,
                        requestId = effectiveContext["requestId"] as? String,
                        round = effectiveContext["toolCallRound"] as? Int,
                        sequence = effectiveContext["toolCallSequence"] as? Int,
                        phase = ToolCallPhase.FINISHED,
                        inputJson = functionInput,
                        context = effectiveContext + mapOf("async" to true),
                        result = result,
                        startedAtUtc = startedAt,
                        finishedAtUtc = finishedAt,
                        durationMillis =
                            java.time.Duration
                                .between(startedAt, finishedAt)
                                .toMillis(),
                    ),
                )
            } finally {
                cancellationRegistration?.close()
            }
        }
    }

    private fun executeWithTimeout(
        tool: VisualAgentTool,
        toolId: String,
        functionInput: String,
        effectiveContext: Map<String, Any>,
        deadlineNanos: Long,
        cancellationToken: ToolCancellationToken,
    ): ToolResult {
        val effectiveTimeoutNanos = remainingNanos(deadlineNanos)
        val future = executor.submit<ToolResult> { tool.execute(functionInput, effectiveContext) }
        val cancellationRegistration = cancellationToken.onCancelled { future.cancel(true) }
        return try {
            future.get(effectiveTimeoutNanos, TimeUnit.NANOSECONDS)
        } catch (_: TimeoutException) {
            cancellationToken.cancel()
            future.cancel(true)
            timeoutFailure(toolId, effectiveTimeoutNanos)
        } catch (_: InterruptedException) {
            cancellationToken.cancel()
            future.cancel(true)
            Thread.currentThread().interrupt()
            failure(toolId, "TOOL_CANCELLED: Tool call was cancelled.")
        } catch (_: CancellationException) {
            failure(toolId, "TOOL_CANCELLED: Tool call was cancelled.")
        } catch (error: Exception) {
            if (cancellationToken.isCancelled) {
                failure(toolId, "TOOL_CANCELLED: Tool call was cancelled.")
            } else {
                val root = generateSequence(error as Throwable?) { it.cause }.lastOrNull()
                failure(toolId, root?.message ?: error.message ?: error::class.simpleName.orEmpty())
            }
        } finally {
            cancellationRegistration.close()
        }
    }

    private fun deadlineNanos(
        context: Map<String, Any>,
        timeoutSeconds: Int,
    ): Long {
        val requested = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds.toLong())
        val inherited = context["toolDeadlineNanos"] as? Long
        return inherited?.coerceAtMost(requested) ?: requested
    }

    private fun remainingNanos(deadlineNanos: Long): Long = (deadlineNanos - System.nanoTime()).coerceAtLeast(0L)

    private fun completeImmediately(
        definition: ToolDefinition,
        functionInput: String,
        context: Map<String, Any>,
        startedAt: Instant,
        result: ToolResult,
    ): String {
        publishEvent(
            definition,
            ToolCallPhase.STARTED,
            functionInput,
            context,
            ToolResult(definition.id.value, true, ""),
            startedAt,
            startedAt,
        )
        publishEvent(
            definition,
            ToolCallPhase.FINISHED,
            functionInput,
            context,
            result,
            startedAt,
            Instant.now(),
        )
        return Json.encodeToString(result)
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
                durationMillis =
                    java.time.Duration
                        .between(startedAt, finishedAt)
                        .toMillis(),
            ),
        )
    }

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
        if (timeoutNanos < TimeUnit.SECONDS.toNanos(1)) {
            "less than 1s"
        } else {
            "${TimeUnit.NANOSECONDS.toSeconds(timeoutNanos)}s"
        }
}
