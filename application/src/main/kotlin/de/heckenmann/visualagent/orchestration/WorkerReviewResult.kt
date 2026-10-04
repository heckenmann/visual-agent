package de.heckenmann.visualagent.orchestration

import de.heckenmann.visualagent.agent.ResponseSchema
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/** Explicit business decision; malformed output is never a business rejection. */
@Serializable
internal enum class WorkerReviewVerdict {
    APPROVED,
    RETRY,
}

/** Completed main-model judgment and the feedback to publish after an approved transition. */
@Serializable
internal data class WorkerReviewResult(
    val verdict: WorkerReviewVerdict,
    val feedback: String,
) {
    /** Whether the reviewer explicitly approved the worker output. */
    val approved: Boolean
        get() = verdict == WorkerReviewVerdict.APPROVED

    /** Provider-neutral schema and strict response parsing. */
    companion object {
        /** The same schema is embedded in the prompt and passed to native provider options. */
        fun schema(): ResponseSchema =
            ResponseSchema(
                """{"type":"object","properties":{"verdict":{"type":"string","enum":["APPROVED","RETRY"]},"feedback":{"type":"string"}},"required":["verdict","feedback"],"additionalProperties":false}""",
            )

        /** Rejects prose, invalid enums, unknown fields, missing fields and empty feedback. */
        fun parse(content: String): WorkerReviewResult {
            val result =
                try {
                    val element = Json.parseToJsonElement(content)
                    if (element !is JsonObject ||
                        element.keys != setOf("verdict", "feedback") ||
                        element.values.any { it !is JsonPrimitive || !it.isString }
                    ) {
                        throw WorkerReviewFormatException()
                    }
                    Json.decodeFromJsonElement<WorkerReviewResult>(element)
                } catch (_: SerializationException) {
                    throw WorkerReviewFormatException()
                }
            if (result.feedback.isBlank()) throw WorkerReviewFormatException()
            return result
        }
    }
}

/** Safe protocol error that deliberately excludes the untrusted model response. */
internal class WorkerReviewFormatException : IllegalArgumentException("Review must contain a valid verdict and non-blank feedback.")

/** Exhausted review evaluation, distinct from a rejected or failed worker execution. */
internal class WorkerReviewFailedException : IllegalStateException("The worker result could not be reviewed; the worker was not rerun.")
