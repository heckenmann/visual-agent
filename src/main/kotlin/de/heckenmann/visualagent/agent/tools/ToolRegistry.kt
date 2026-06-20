package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.ToolDefinition
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import de.heckenmann.visualagent.config.AppConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.ToolCallback
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.springframework.ai.tool.definition.ToolDefinition as SpringToolDefinition

/**
 * Registry that converts application tools into request-scoped Spring AI callbacks.
 */
@Service
class ToolRegistry(
    tools: List<VisualAgentTool>,
    private val toolEventBus: ToolEventBus,
) : DisposableBean {
    private val toolsById = tools.associateBy { it.definition.id }
    private val executor = Executors.newCachedThreadPool()

    /**
     * Return all registered application tool IDs.
     *
     * @return Tool IDs known by the registry
     */
    fun allToolIds(): Set<ToolId> = toolsById.keys

    /**
     * Return all registered tool definitions.
     *
     * @return Tool definitions known by the registry
     */
    fun toolDefinitions(): List<ToolDefinition> = toolsById.values.map { it.definition }.sortedBy { it.id.value }

    /**
     * Resolve registered tools by ID.
     *
     * @param enabledTools Tool IDs requested for a model call
     * @return Matching registered tools in deterministic order
     */
    fun resolve(enabledTools: Set<ToolId>): List<VisualAgentTool> =
        enabledTools.mapNotNull { toolsById[it] }.sortedBy { it.definition.name }

    /**
     * Build Spring AI callbacks for the requested tools.
     *
     * @param enabledTools Tool IDs requested for a model call
     * @param context Request-scoped execution metadata passed to each tool
     * @return Tool callbacks that can be attached to Spring AI options
     */
    fun functionCallbacks(
        enabledTools: Set<ToolId>,
        context: Map<String, Any> = emptyMap(),
    ): List<ToolCallback> =
        resolve(enabledTools).map { tool ->
            val definition = tool.definition
            /**
             * Spring AI callback wrapper that emits lifecycle events around one Visual Agent tool.
             */
            object : ToolCallback {
                override fun getToolDefinition(): SpringToolDefinition =
                    SpringToolDefinition
                        .builder()
                        .name(definition.name)
                        .description(definition.description)
                        .inputSchema(definition.inputSchema)
                        .build()

                override fun call(functionInput: String): String = call(functionInput, ToolContext(context))

                override fun call(
                    functionInput: String,
                    toolContext: ToolContext?,
                ): String {
                    val effectiveContext = context + (toolContext?.context ?: emptyMap())
                    val inputObject = parseObject(functionInput)
                    val options = runtimeOptions(inputObject, AppConfig.instance.timeoutSeconds)
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
            }
        }

    override fun destroy() {
        executor.shutdownNow()
    }

    private fun scheduleAsyncExecution(
        tool: VisualAgentTool,
        definition: de.heckenmann.visualagent.agent.ToolDefinition,
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
        } catch (error: Exception) {
            val root = generateSequence(error as Throwable?) { it.cause }.lastOrNull()
            failure(toolId, root?.message ?: error.message ?: error::class.simpleName.orEmpty())
        }
    }
}
