package de.heckenmann.visualagent.agent.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Resource limits for one independent, non-atomic batch. */
data class ToolBatchLimits(
    val maxItems: Int = 32,
    val maxConcurrency: Int = 4,
    val maxArgumentCharacters: Int = 262144,
    val maxResultCharacters: Int = 65536,
    val timeoutMillis: Long = 60000,
)

/** One caller identity and canonical tool invocation. */
data class ToolBatchItem(
    val id: String,
    val tool: String,
    val arguments: JsonObject,
)

/** Immutable request metadata and limits shared by every child. */
data class ToolBatchRequest(
    val items: List<ToolBatchItem>,
    val enabledTools: Set<String>,
    val context: Map<String, Any> = emptyMap(),
    val limits: ToolBatchLimits = ToolBatchLimits(),
    val batchId: String =
        java.util.UUID
            .randomUUID()
            .toString(),
)

/** Terminal child state; a batch never rolls back successful siblings. */
@Serializable
enum class ToolBatchStatus { SUCCESS, FAILURE, SKIPPED, CANCELLED }

/** Bounded per-item result, returned in declaration order. */
@Serializable
data class ToolBatchOutcome(
    val id: String,
    val name: String,
    val status: ToolBatchStatus,
    val result: de.heckenmann.visualagent.agent.tools.api.ToolResultEnvelope,
    val durationMillis: Long = 0,
) {
    /** Indicates whether the canonical child result succeeded. */
    val success: Boolean get() = result.success

    /** Provides a compact diagnostic for immediate JavaScript argument-error rejection. */
    val error: String? get() = result.error?.message

    /** Identifies argument errors that must abort a batch immediately. */
    val errorCode: de.heckenmann.visualagent.agent.tools.api.ToolErrorCode? get() = result.error?.code
}

/** Rejects an entire batch before any child starts. */
class ToolBatchValidationException(
    val category: String,
    message: String,
) : IllegalArgumentException(message)
