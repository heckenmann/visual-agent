package de.heckenmann.visualagent.agent

import kotlinx.serialization.Serializable

/**
 * A normalized outcome of exactly one provider model turn.
 *
 * This intentionally keeps provider-facing state separate from the compact
 * [ChatResponse] consumed by conversation code. It contains no raw provider
 * payload and only carries identifiers and values that are safe to expose
 * across the provider boundary.
 *
 * @property model Provider-selected model identifier
 * @property content Assistant-visible content for this turn
 * @property reasoning Provider-supplied reasoning or reasoning summary
 * @property reasoningIsSummary Whether [reasoning] is a provider-designated safe summary
 * @property toolCalls Structured tool calls requested in this turn
 * @property finishReason Typed terminal state reported by the provider
 * @property refusal Safe refusal or filtering summary when supplied
 * @property usage Token usage reported by the provider
 * @property timing Timing values reported by the provider
 * @property metadata Allowlisted provider metadata
 * @see docs/usecases/uc_0000020_execute_tool_call.md
 */
@Serializable
data class ProviderTurnResponse(
    val model: String,
    val content: String,
    val reasoning: String? = null,
    val reasoningIsSummary: Boolean = false,
    val toolCalls: List<ProviderToolCall> = emptyList(),
    val finishReason: ProviderFinishReason? = null,
    val refusal: String? = null,
    val usage: ProviderTokenUsage? = null,
    val timing: ProviderResponseTiming? = null,
    val metadata: ProviderResponseMetadata = ProviderResponseMetadata(),
)

/**
 * One model-requested function call with a request-scoped identity.
 *
 * @property id Provider-issued or deterministic fallback call identifier
 * @property type Provider call type, normally `function`
 * @property functionName Provider-facing callback name
 * @property argumentsJson Exact JSON arguments used for execution only
 */
@Serializable
data class ProviderToolCall(
    val id: String,
    val type: String,
    val functionName: String,
    val argumentsJson: String,
)

/**
 * Provider-neutral terminal reason for a model turn.
 */
@Serializable
enum class ProviderFinishReason {
    /** Provider stopped after producing a normal assistant response. */
    STOP,

    /** Provider requested one or more tool calls. */
    TOOL_CALLS,

    /** Provider reached a configured output limit. */
    LENGTH,

    /** Provider refused the request. */
    REFUSAL,

    /** Provider filtered the generated content. */
    CONTENT_FILTER,

    /** The request was cancelled before normal completion. */
    CANCELLED,

    /** The provider reported a terminal error. */
    ERROR,

    /** Provider supplied an unsupported terminal reason. */
    UNKNOWN,
}

/**
 * Token counts reported for one model turn.
 *
 * @property promptTokens Input token count
 * @property completionTokens Generated token count
 * @property totalTokens Total token count
 * @property reasoningTokens Provider-reported reasoning token count
 * @property cachedPromptTokens Cached input token count
 */
@Serializable
data class ProviderTokenUsage(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val reasoningTokens: Int? = null,
    val cachedPromptTokens: Long? = null,
)

/**
 * Human-scale timing measurements for one provider turn.
 *
 * @property totalMillis Total provider processing time
 * @property timeToFirstTokenMillis Time until the first generated token
 * @property promptEvaluationMillis Input evaluation time
 * @property generationMillis Generated-token evaluation time
 */
@Serializable
data class ProviderResponseTiming(
    val totalMillis: Long? = null,
    val timeToFirstTokenMillis: Long? = null,
    val promptEvaluationMillis: Long? = null,
    val generationMillis: Long? = null,
)

/**
 * Explicitly allowlisted provider metadata safe for diagnostics and correlation.
 *
 * @property responseId Provider response identifier
 * @property rawFinishReason Provider's unmodified finish-reason value
 * @property systemFingerprint Provider system fingerprint when available
 * @property createdAtIso Provider creation timestamp in ISO-8601 form
 * @property requestId Visual Agent request identifier
 * @property round Zero-based tool-loop round
 * @property sequence Zero-based response sequence within the request
 */
@Serializable
data class ProviderResponseMetadata(
    val responseId: String? = null,
    val rawFinishReason: String? = null,
    val systemFingerprint: String? = null,
    val createdAtIso: String? = null,
    val requestId: String? = null,
    val round: Int? = null,
    val sequence: Int? = null,
)
