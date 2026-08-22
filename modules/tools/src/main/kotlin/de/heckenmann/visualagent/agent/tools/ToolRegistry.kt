package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
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
    private val defaultTimeoutSeconds: () -> Int = { 60 },
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
            .map(VisualAgentTool::definition)
            .sortedBy { it.id.value }

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
        val options = runtimeOptions(inputObject, defaultTimeoutSeconds())
        val effectiveContext = context + mapOf("toolTimeoutSeconds" to options.timeoutSeconds)
        val startedAt = Instant.now()
        toolEventBus.publish(
            ToolCallEvent(
                toolId = definition.id.value,
                functionName = definition.name,
                phase = ToolCallPhase.STARTED,
                inputJson = functionInput,
                context = effectiveContext,
                result =
                    ToolResult(
                        toolId = definition.id.value,
                        success = true,
                        content = "",
                    ),
                startedAtUtc = startedAt,
                finishedAtUtc = startedAt,
                durationMillis = 0L,
            ),
        )
        if (tool.managesExecution) {
            val result =
                runCatching { tool.execute(functionInput, effectiveContext) }
                    .getOrElse { error ->
                        failure(
                            definition.id.value,
                            error.message ?: error::class.simpleName.orEmpty(),
                        )
                    }
            val finishedAt = Instant.now()
            toolEventBus.publish(
                ToolCallEvent(
                    toolId = definition.id.value,
                    functionName = definition.name,
                    phase = ToolCallPhase.FINISHED,
                    inputJson = functionInput,
                    context = effectiveContext + mapOf("managedExecution" to true),
                    result = result,
                    startedAtUtc = startedAt,
                    finishedAtUtc = finishedAt,
                    durationMillis =
                        java.time.Duration
                            .between(startedAt, finishedAt)
                            .toMillis(),
                ),
            )
            return Json.encodeToString(result)
        }
        if (options.async) {
            scheduleAsyncExecution(
                tool = tool,
                definition = definition,
                functionInput = functionInput,
                effectiveContext = effectiveContext,
                timeoutSeconds = options.timeoutSeconds,
                startedAt = startedAt,
            )
            val accepted =
                success(
                    definition.id.value,
                    "scheduled async tool call (timeout=${options.timeoutSeconds}s)",
                )
            return Json.encodeToString(accepted)
        }
        val result = executeWithTimeout(tool, definition.id.value, functionInput, effectiveContext, options.timeoutSeconds)
        val finishedAt = Instant.now()
        toolEventBus.publish(
            ToolCallEvent(
                toolId = definition.id.value,
                functionName = definition.name,
                phase = ToolCallPhase.FINISHED,
                inputJson = functionInput,
                context = effectiveContext,
                result = result,
                startedAtUtc = startedAt,
                finishedAtUtc = finishedAt,
                durationMillis =
                    java.time.Duration
                        .between(startedAt, finishedAt)
                        .toMillis(),
            ),
        )
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
        timeoutSeconds: Int,
        startedAt: Instant,
    ) {
        executor.submit {
            val result = executeWithTimeout(tool, definition.id.value, functionInput, effectiveContext, timeoutSeconds)
            val finishedAt = Instant.now()
            toolEventBus.publish(
                ToolCallEvent(
                    toolId = definition.id.value,
                    functionName = definition.name,
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
        }
    }

    private fun executeWithTimeout(
        tool: VisualAgentTool,
        toolId: String,
        functionInput: String,
        effectiveContext: Map<String, Any>,
        timeoutSeconds: Int,
    ): ToolResult {
        val future = executor.submit<ToolResult> { tool.execute(functionInput, effectiveContext) }
        return try {
            future.get(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            failure(toolId, "Tool call timed out after ${timeoutSeconds}s")
        } catch (_: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            failure(toolId, "Tool call was cancelled")
        } catch (error: Exception) {
            val root = generateSequence(error as Throwable?) { it.cause }.lastOrNull()
            failure(toolId, root?.message ?: error.message ?: error::class.simpleName.orEmpty())
        }
    }
}
