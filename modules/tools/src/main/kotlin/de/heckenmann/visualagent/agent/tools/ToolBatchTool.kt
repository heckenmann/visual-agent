package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import reactor.core.publisher.Mono

/** Provider-neutral independent batch entry point; every child uses the shared registry executor. */
class ToolBatchTool(
    private val executor: () -> ToolBatchExecutor,
) : VisualAgentTool {
    override val allowsDetachedExecution = false

    override val definition =
        ToolDefinition(
            ToolId("tools:batch"),
            "tools_batch",
            "Prefer bundling independent enabled tool calls into one native tool-call group or this batch whenever possible. " +
                "Avoid separate model rounds for calls that do not depend on each other's results. " +
                "Use canonical function names in tool; results retain declaration order. " +
                "Keep dependent calls sequential. Do not include JavaScript execution, help dispatch or another batch. " +
                "Batches are non-atomic: successful mutations are not rolled back. The server selects safe concurrency.",
            """{"type":"object","properties":{"calls":{"type":"array","minItems":1,"maxItems":32,"items":{"type":"object","properties":{"id":{"type":"string"},"tool":{"type":"string"},"arguments":{"type":"object"}},"required":["id","tool","arguments"],"additionalProperties":false}}},"required":["calls"],"additionalProperties":false}""",
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult = throw UnsupportedOperationException("Tool batches use the reactive registry contract")

    override fun executeReactive(
        inputJson: String,
        context: Map<String, Any>,
    ): Mono<ToolResult> =
        Mono.defer {
            val calls =
                parseObject(inputJson)["calls"] as? JsonArray
                    ?: throw ToolBatchValidationException("TOOL_ARGUMENTS", "calls must be an array")
            val items =
                calls.map { value ->
                    val item = value as? JsonObject ?: throw ToolBatchValidationException("TOOL_ARGUMENTS", "Batch item must be an object")
                    ToolBatchItem(
                        (item["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty(),
                        (item["tool"] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty(),
                        item["arguments"] as? JsonObject
                            ?: throw ToolBatchValidationException("TOOL_ARGUMENTS", "Batch arguments must be an object"),
                    )
                }
            val enabled = (context["enabledTools"] as? Set<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
            executor().execute(ToolBatchRequest(items, enabled, context)).map { outcomes ->
                val data = Json.parseToJsonElement(Json.encodeToString(outcomes))
                ToolResult("tools:batch", true, "Batch completed; inspect each item outcome", data = data)
            }
        }
}
