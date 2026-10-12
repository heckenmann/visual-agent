package de.heckenmann.visualagent.task

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Worker-reported outcome, distinct from server verification and final acceptance. */
@Serializable
enum class TaskOutcome { PRODUCED, PARTIAL, BLOCKED }

/** Check outcome; absent or unsupported execution is NOT_CHECKED, never a pass. */
@Serializable
enum class TaskCheckState { PASSED, FAILED, NOT_CHECKED }

/** Independent review proposal, separate from the final guarded acceptance decision. */
@Serializable
enum class TaskReviewState { APPROVED, RETRY, FAILED }

/** Worker artifact reference; version is asserted only after server observation. */
@Serializable
data class TaskArtifact(
    val id: String,
    val path: String,
)

/** Explicit source attribution supporting a material claim. */
@Serializable
data class TaskSource(
    val inputId: String,
    val claim: String,
)

/** Worker report only; it cannot forge trusted identifiers or verified check outcomes. */
@Serializable
data class WorkerHandoff(
    val summary: String,
    val outcome: TaskOutcome,
    val artifacts: List<TaskArtifact> = emptyList(),
    val sources: List<TaskSource> = emptyList(),
    val blockers: List<String> = emptyList(),
    val nextAction: String? = null,
) {
    /** Strict decoding prevents worker JSON from setting server-owned provenance. */
    companion object {
        /** Provider-neutral schema for the worker-only report; trusted provenance is absent. */
        fun schema(): String =
            """{"type":"object","properties":{"summary":{"type":"string"},"outcome":{"enum":["PRODUCED","PARTIAL","BLOCKED"]},"artifacts":{"type":"array","items":{"type":"object","properties":{"id":{"type":"string"},"path":{"type":"string"}},"required":["id","path"],"additionalProperties":false}},"sources":{"type":"array","items":{"type":"object","properties":{"inputId":{"type":"string"},"claim":{"type":"string"}},"required":["inputId","claim"],"additionalProperties":false}},"blockers":{"type":"array","items":{"type":"string"}},"nextAction":{"type":"string"}},"required":["summary","outcome"],"additionalProperties":false}"""

        /** Validates worker references against the immutable contract. */
        fun parse(
            text: String,
            contract: TaskContract,
        ): WorkerHandoff {
            require(text.length <= TaskContractCodec.MAX_CHARACTERS) { "Worker handoff exceeds the size limit" }
            val result = Json.decodeFromString<WorkerHandoff>(text)
            require(result.summary.isNotBlank() && result.summary.length <= 8_192) { "Worker summary must be bounded and non-blank" }
            require(
                result.artifacts.size <= 32 &&
                    result.artifacts
                        .map {
                            it.id
                        }.distinct()
                        .size == result.artifacts.size,
            ) { "Invalid artifact references" }
            result.artifacts.forEach { artifact ->
                require(
                    contract.deliverables.any { it.id == artifact.id && it.path == artifact.path },
                ) { "Artifact does not match the task contract" }
            }
            require(
                result.sources.size <= 32 &&
                    result.sources.all { source ->
                        contract.inputs.any { it.id == source.inputId } && source.claim.length <= 2_000
                    },
            ) { "Invalid source references" }
            require(result.blockers.size <= 32 && result.blockers.all { it.length <= 2_000 }) { "Invalid blockers" }
            require(result.nextAction == null || result.nextAction.length <= 2_000) { "Next action exceeds the limit" }
            return result
        }
    }
}

/** Server-observed artifact identity; a digest establishes version, not correctness. */
@Serializable
data class ObservedArtifact(
    val id: String,
    val path: String,
    val sha256: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val state: TaskCheckState,
    val detail: String,
    val currentState: TaskCheckState? = null,
)

/** Server-owned criterion evidence independent of worker claims. */
@Serializable
data class TaskObservation(
    val criterionId: String,
    val state: TaskCheckState,
    val detail: String,
)

/** Durable, idempotent attempt record with trusted provenance and explicit acceptance. */
@Serializable
data class TaskAttemptResult(
    val todoId: String,
    val agentId: String,
    val executionId: String,
    val attempt: Int,
    val contractRevision: Long,
    val promptVersion: String,
    val reported: WorkerHandoff,
    val artifacts: List<ObservedArtifact>,
    val observations: List<TaskObservation>,
    val toolEvidence: List<TaskToolObservation> = emptyList(),
    val reviewFeedback: String? = null,
    val reviewVerdict: TaskReviewState? = null,
    val accepted: Boolean = false,
    val currentAccepted: Boolean = false,
    val version: Int = 1,
    val contractSnapshot: TaskContract? = null,
    val inputVersions: Map<String, String?> = emptyMap(),
) {
    /** Bounded JSON for detail retrieval and persistence. */
    fun encode(): String =
        resultJson.encodeToString(this).also { require(it.length <= 262_144) { "Attempt record exceeds the size limit" } }

    private companion object {
        val resultJson = Json { encodeDefaults = true }
    }
}

/** Terminal tool observation without persisting raw secret-bearing output. */
@Serializable
data class TaskToolObservation(
    val tool: String,
    val success: Boolean,
    val asynchronous: Boolean,
)
