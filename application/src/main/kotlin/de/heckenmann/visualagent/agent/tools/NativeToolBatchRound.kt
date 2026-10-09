package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.ProviderToolCall
import de.heckenmann.visualagent.agent.tools.api.ToolError
import de.heckenmann.visualagent.agent.tools.api.ToolErrorCode
import de.heckenmann.visualagent.agent.tools.api.ToolResultEnvelope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import reactor.core.publisher.Mono

/** Maps provider calls once into immutable child contexts; no thread-local identities cross workers. */
internal class NativeToolBatchRound(
    private val registry: ToolRegistry,
    private val batches: ToolBatchExecutor,
) {
    /** Preserves single-call compatibility and delegates independent native groups to the shared executor. */
    fun execute(
        calls: List<ProviderToolCall>,
        round: Int,
        parent: String?,
        names: Set<String>,
        context: Map<String, Any>,
    ): Mono<List<String>> =
        Mono.defer {
            val available =
                registry
                    .resolve(
                        registry.allToolIds(),
                    ).filter { it.definition.name in names }
                    .associateBy { it.definition.name }
            val enabled = available.values.map { it.definition.id.value }.toSet()
            val metadata =
                context + mapOf("enabledTools" to enabled, "toolCallRound" to round) +
                    (parent?.let { mapOf("parentAssistantTurnId" to it) } ?: emptyMap())
            if (calls.size == 1) {
                val call = calls.single()
                val tool = available[call.functionName] ?: throw ToolBatchValidationException("TOOL_ACCESS", "Provider tool is not enabled")
                registry
                    .executeReactive(
                        tool,
                        call.argumentsJson,
                        metadata + mapOf("providerToolCallId" to call.id, "toolCallSequence" to 0),
                    ).map { listOf(it) }
            } else {
                val items =
                    calls.map { call ->
                        val tool =
                            available[call.functionName]
                                ?: throw ToolBatchValidationException("TOOL_ACCESS", "Provider tool is not enabled")
                        ToolBatchItem(
                            call.id,
                            tool.definition.id.value,
                            Json.parseToJsonElement(call.argumentsJson) as? JsonObject
                                ?: throw ToolBatchValidationException("TOOL_ARGUMENTS", "Tool arguments must be an object"),
                        )
                    }
                batches.execute(ToolBatchRequest(items, enabled, metadata + ("batchProviderCalls" to true))).map { outcomes ->
                    outcomes.mapIndexed { index, outcome ->
                        val data = outcome.content?.let { runCatching { Json.parseToJsonElement(it) }.getOrElse { _ -> JsonPrimitive(it) } }
                        Json.encodeToString(
                            ToolResultEnvelope(
                                items[index].tool,
                                outcome.success,
                                data ?: kotlinx.serialization.json.JsonNull,
                                if (outcome.success) {
                                    null
                                } else {
                                    ToolError(
                                        outcome.errorCode ?: ToolErrorCode.EXECUTION_FAILED,
                                        outcome.error ?: "Tool failed",
                                        "Inspect this item before explicitly retrying.",
                                        false,
                                    )
                                },
                            ),
                        )
                    }
                }
            }
        }
}
