package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolError
import de.heckenmann.visualagent.agent.tools.api.ToolErrorCode
import de.heckenmann.visualagent.agent.tools.api.ToolResultEnvelope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/** Pure bounded projections of the existing single-call envelope, including failed-call data. */
internal object ToolBatchResults {
    fun capacity(request: ToolBatchRequest): Int = (request.limits.maxResultCharacters - 2) / request.items.size - 1

    fun overhead(
        item: ToolBatchItem,
        tool: VisualAgentTool,
    ): Int =
        Json
            .encodeToString(
                ToolBatchOutcome(
                    item.id,
                    tool.definition.name,
                    ToolBatchStatus.CANCELLED,
                    ToolResultEnvelope(
                        tool.definition.id.value,
                        false,
                        JsonPrimitive(""),
                        ToolError(ToolErrorCode.PERMISSION_DENIED, "", "", false),
                    ),
                    Long.MAX_VALUE,
                ),
            ).length + 128

    fun collect(
        request: ToolBatchRequest,
        item: ToolBatchItem,
        tool: VisualAgentTool,
        result: ToolResultEnvelope,
        duration: Long,
    ): ToolBatchOutcome {
        val limit = (capacity(request) - overhead(item, tool)).coerceAtLeast(0) / 6
        val dataLimit = if (result.error == null) limit else limit / 2
        val messageLimit = limit / 4
        val data = result.data.toString()
        val bounded =
            result.copy(
                toolId = tool.definition.id.value,
                data = if (data.length <= dataLimit) result.data else JsonPrimitive(text(data, dataLimit)),
                error =
                    result.error?.copy(
                        message = text(result.error.message, messageLimit),
                        remediation = text(result.error.remediation, limit - dataLimit - messageLimit),
                    ),
            )
        val status =
            when {
                result.success -> ToolBatchStatus.SUCCESS
                result.error?.code == ToolErrorCode.CANCELLED -> ToolBatchStatus.CANCELLED
                else -> ToolBatchStatus.FAILURE
            }
        return ToolBatchOutcome(item.id, tool.definition.name, status, bounded, duration)
    }

    fun failure(
        item: ToolBatchItem,
        tool: VisualAgentTool,
        status: ToolBatchStatus,
        code: ToolErrorCode,
        message: String,
    ): ToolBatchOutcome =
        ToolBatchOutcome(
            item.id,
            tool.definition.name,
            status,
            ToolResultEnvelope(
                tool.definition.id.value,
                false,
                JsonNull,
                ToolError(code, message, "Inspect completed outcomes before explicitly retrying unfinished work.", false),
            ),
        )

    private fun text(
        value: String,
        limit: Int,
    ): String {
        if (value.length <= limit) return value
        if (limit < 13) return "…".take(limit)
        return value.take(limit - 13) + " [truncated]"
    }
}

/** Signals an already-recorded invalid argument outcome without waiting for slow siblings. */
internal class ToolBatchSyntaxFailure : RuntimeException("Batch child arguments are invalid")
