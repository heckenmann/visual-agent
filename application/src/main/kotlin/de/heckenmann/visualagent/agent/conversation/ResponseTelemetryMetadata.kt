package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.agent.ProviderFinishReason
import de.heckenmann.visualagent.agent.ProviderTurnResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Stores and reads the safe, presentation-only subset of a provider turn.
 *
 * Tool arguments, tool results, raw provider payloads, and credentials are
 * deliberately excluded from persisted conversation metadata. Provider
 * reasoning is retained only when the caller explicitly permits it and the
 * provider marks it as a presentation-safe summary.
 */
internal object ResponseTelemetryMetadata {
    /** Encodes telemetry as conversation metadata. */
    fun encode(
        turn: ProviderTurnResponse,
        includeReasoning: Boolean = false,
    ): String =
        buildJsonObject {
            put(
                RESPONSE_TELEMETRY_KEY,
                Json.parseToJsonElement(
                    Json.encodeToString(
                        PersistedResponseTelemetry(
                            model = turn.model,
                            reasoning = turn.reasoning.takeIf { includeReasoning && turn.reasoningIsSummary },
                            finishReason = turn.finishReason,
                            totalMillis = turn.timing?.totalMillis,
                            timeToFirstTokenMillis = turn.timing?.timeToFirstTokenMillis,
                            promptEvaluationMillis = turn.timing?.promptEvaluationMillis,
                            generationMillis = turn.timing?.generationMillis,
                            promptTokens = turn.usage?.promptTokens,
                            completionTokens = turn.usage?.completionTokens,
                            totalTokens = turn.usage?.totalTokens,
                        ),
                    ),
                ),
            )
        }.toString()

    /** Decodes telemetry only when the metadata has the expected safe shape. */
    fun decode(metadata: String?): PersistedResponseTelemetry? =
        runCatching {
            val element = metadata?.let(Json::parseToJsonElement)?.jsonObject ?: return null
            val telemetry = element[RESPONSE_TELEMETRY_KEY] ?: return null
            Json.decodeFromString<PersistedResponseTelemetry>(telemetry.toString())
        }.getOrNull()

    private const val RESPONSE_TELEMETRY_KEY = "responseTelemetry"
}

/** Safe provider-turn values retained for conversation diagnostics and presentation. */
@Serializable
internal data class PersistedResponseTelemetry(
    val model: String,
    val reasoning: String? = null,
    val finishReason: ProviderFinishReason? = null,
    val totalMillis: Long? = null,
    val timeToFirstTokenMillis: Long? = null,
    val promptEvaluationMillis: Long? = null,
    val generationMillis: Long? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
)
