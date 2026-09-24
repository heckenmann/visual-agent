package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Provider-neutral registry and execution boundary for model-callable tools.
 *
 * Use cases: UC-0000019, UC-0000020, UC-0000042, UC-0000043, UC-0000044.
 */
class ToolRegistry(
    tools: List<VisualAgentTool>,
    private val toolEventBus: ToolEventBus,
    private val defaultTimeoutSeconds: () -> Int = { DEFAULT_TOOL_TIMEOUT_SECONDS },
) {
    private val logger = KotlinLogging.logger {}
    private val registeredTools = tools.toList()
    private val toolsById = registeredTools.associateBy { it.definition.id }
    private val reactiveExecution = ReactiveToolExecution(toolEventBus)

    init {
        validateDefinitions()
    }

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
     * Executes one registered tool with lifecycle events, timeout handling, and cancellation.
     *
     * @param tool resolved tool
     * @param functionInput JSON arguments from the provider
     * @param context request-scoped metadata
     * @return Deferred serialized structured result
     * @see docs/usecases/uc_0000020_execute_tool_call.md
     */
    fun executeReactive(
        tool: VisualAgentTool,
        functionInput: String,
        context: Map<String, Any>,
    ): Mono<String> {
        val definition = tool.definition
        val inputObject = parseObject(functionInput)
        return Mono.defer {
            val startedAt = Instant.now()
            val options =
                runCatching { runtimeOptions(inputObject, defaultTimeoutSeconds()) }
                    .getOrElse { error ->
                        return@defer Mono.just(
                            completeImmediately(
                                definition,
                                functionInput,
                                context + mapOf("toolTimeoutSeconds" to defaultTimeoutSeconds()),
                                startedAt,
                                failure(
                                    definition.id.value,
                                    "TOOL_ARGUMENTS: ${error.message ?: "Invalid tool runtime arguments."}",
                                ),
                            ),
                        )
                    }
            val deadlineNanos = deadlineNanos(context, options.timeoutSeconds)
            if (remainingNanos(deadlineNanos) <= 0L) {
                return@defer Mono.just(
                    completeImmediately(
                        definition,
                        functionInput,
                        context + mapOf("toolTimeoutSeconds" to options.timeoutSeconds),
                        startedAt,
                        timeoutFailure(definition.id.value, 0L),
                    ),
                )
            }
            val execution =
                reactiveExecution.execute(
                    tool = tool,
                    definition = definition,
                    functionInput = functionInput,
                    context = context,
                    options = options,
                    deadlineNanos = deadlineNanos,
                    startedAt = startedAt,
                )
            if (options.async) {
                execution.subscribe(
                    {},
                    { error -> logger.warn(error) { "Asynchronous tool execution failed for toolId=${definition.id.value}." } },
                )
                Mono.just(
                    serialize(
                        success(
                            definition.id.value,
                            "scheduled async tool call (timeout=${options.timeoutSeconds}s)",
                        ),
                    ),
                )
            } else {
                execution.map(::serialize)
            }
        }
    }

    /**
     * Executes a tool for a synchronous host callback such as Spring AI's [org.springframework.ai.tool.ToolCallback].
     *
     * This is a deliberate adapter boundary. Server-side tool execution itself remains [Mono]-based;
     * new server code must use [executeReactive] instead.
     */
    fun executeBlocking(
        tool: VisualAgentTool,
        functionInput: String,
        context: Map<String, Any>,
    ): String = checkNotNull(executeReactive(tool, functionInput, context).block()) { "Tool execution completed without a result." }

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
        return serialize(result)
    }

    private fun serialize(result: ToolResult): String = envelopeJson.encodeToString(ToolResultNormalization.envelope(result))

    private fun validateDefinitions() {
        val definitions = registeredTools.map(::definition)
        require(definitions.map { it.id }.distinct().size == definitions.size) {
            "Tool registry contains duplicate internal tool IDs."
        }
        definitions.forEach { definition ->
            val expectedFunctionName = definition.id.toFunctionName()
            require(definition.name == expectedFunctionName) {
                "Tool '${definition.id.value}' must use provider function name '$expectedFunctionName'."
            }
        }
        require(definitions.map { it.name }.distinct().size == definitions.size) {
            "Tool registry contains provider function name collisions."
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
                parentAssistantTurnId = context["parentAssistantTurnId"] as? String,
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

    private companion object {
        val envelopeJson =
            Json {
                encodeDefaults = true
                explicitNulls = true
            }
    }
}
