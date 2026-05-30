package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.ToolResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.ai.model.function.FunctionCallback
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Registry for all tools that can be exposed to the LLM.
 *
 * The registry is the only place that maps application tool IDs to Spring AI function
 * callbacks, allowing providers and agents to stay provider-neutral.
 */
@Service
class ToolRegistry(
    tools: List<VisualAgentTool>,
    private val toolEventBus: ToolEventBus,
) {
    private val toolsById = tools.associateBy { it.definition.id }

    /**
     * Return all registered application tool IDs.
     *
     * @return Tool IDs known by the registry
     */
    fun allToolIds(): Set<ToolId> = toolsById.keys

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
     * @return Function callbacks that can be attached to Spring AI options
     */
    fun functionCallbacks(
        enabledTools: Set<ToolId>,
        context: Map<String, Any> = emptyMap(),
    ): List<FunctionCallback> =
        resolve(enabledTools).map { tool ->
            val definition = tool.definition
            object : FunctionCallback {
                override fun getName(): String = definition.name

                override fun getDescription(): String = definition.description

                override fun getInputTypeSchema(): String = definition.inputSchema

                override fun call(functionInput: String): String {
                    val startedAt = Instant.now()
                    val result = runCatching { tool.execute(functionInput, context) }
                        .getOrElse { error ->
                            ToolResult(
                                toolId = definition.id.value,
                                success = false,
                                content = "",
                                error = error.message ?: error::class.simpleName,
                            )
                        }
                    val finishedAt = Instant.now()
                    toolEventBus.publish(
                        ToolCallEvent(
                            toolId = definition.id.value,
                            functionName = definition.name,
                            inputJson = functionInput,
                            context = context,
                            result = result,
                            startedAtUtc = startedAt,
                            finishedAtUtc = finishedAt,
                            durationMillis = java.time.Duration.between(startedAt, finishedAt).toMillis(),
                        ),
                    )
                    return Json.encodeToString(result)
                }
            }
        }
}
