package de.heckenmann.visualagent.agent

import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.ai.chat.model.ChatResponse as SpringChatResponse

/**
 * Converts Spring AI responses into the provider-neutral turn model.
 *
 * Spring AI remains the provider integration boundary. This mapper deliberately
 * extracts only portable semantics and an explicit allowlist of safe metadata;
 * raw SDK payloads must not cross into application or UI code.
 */
internal object ProviderTurnResponseMapper {
    /**
     * Normalizes one Spring AI response.
     *
     * @param response Spring AI response to map
     * @param requestId Optional Visual Agent request identifier
     * @param round Zero-based tool-loop round
     * @param sequence Zero-based response sequence
     * @return Provider-neutral structured model turn
     */
    fun fromSpring(
        response: SpringChatResponse,
        requestId: String? = null,
        round: Int? = null,
        sequence: Int? = null,
    ): ProviderTurnResponse {
        val generation = response.result
        val generationMetadata = generation?.metadata
        val rawFinishReason = generationMetadata?.finishReason
        val responseId = response.metadata.id.takeIf(String::isNotBlank)
        return ProviderTurnResponse(
            model = response.metadata.model,
            content = generation?.output?.text.orEmpty(),
            toolCalls =
                generation
                    ?.output
                    ?.toolCalls
                    .orEmpty()
                    .mapIndexed { index, call ->
                        ProviderToolCall(
                            id = call.id().takeIf(String::isNotBlank) ?: fallbackCallId(requestId, round, sequence, index),
                            type = call.type().takeIf(String::isNotBlank) ?: "function",
                            functionName = call.name(),
                            argumentsJson = call.arguments(),
                        )
                    },
            finishReason = normalizeFinishReason(rawFinishReason),
            refusal = refusalSummary(generationMetadata),
            usage =
                ProviderTokenUsage(
                    promptTokens = response.metadata.usage.promptTokens,
                    completionTokens = response.metadata.usage.completionTokens,
                    totalTokens = response.metadata.usage.totalTokens,
                    cachedPromptTokens = response.metadata.usage.cacheReadInputTokens,
                ).takeUnless { it == ProviderTokenUsage() },
            metadata =
                ProviderResponseMetadata(
                    responseId = responseId,
                    rawFinishReason = rawFinishReason,
                    requestId = requestId,
                    round = round,
                    sequence = sequence,
                ),
        )
    }

    /**
     * Creates the existing compact response from a normalized model turn.
     *
     * @param turn Structured provider turn
     * @return Conversation-facing response with the original structured turn attached
     */
    fun toChatResponse(turn: ProviderTurnResponse): ChatResponse =
        ChatResponse(
            model = turn.model,
            message = Message(role = "assistant", content = turn.content),
            done = turn.finishReason != null,
            totalDuration = turn.timing?.totalMillis?.times(NANOS_PER_MILLI),
            promptEvalCount = turn.usage?.promptTokens,
            evalCount = turn.usage?.completionTokens,
            providerTurn = turn,
        )

    /**
     * Normalizes a native tool-less Ollama response without losing its timing
     * or native thinking field.
     *
     * @param response Native Ollama response
     * @param requestId Optional Visual Agent request identifier
     * @param sequence Zero-based stream sequence
     * @return Provider-neutral structured model turn
     */
    fun fromOllama(
        response: OllamaApi.ChatResponse,
        requestId: String? = null,
        sequence: Int? = null,
    ): ProviderTurnResponse {
        val rawFinishReason = response.doneReason() ?: response.done()?.takeIf { it }?.let { "stop" }
        return ProviderTurnResponse(
            model = response.model(),
            content = response.message().content().orEmpty(),
            reasoning = response.message().thinking()?.takeIf { it.isNotBlank() },
            finishReason = normalizeFinishReason(rawFinishReason),
            usage =
                ProviderTokenUsage(
                    promptTokens = response.promptEvalCount(),
                    completionTokens = response.evalCount(),
                    totalTokens = response.promptEvalCount()?.plus(response.evalCount() ?: 0),
                ),
            timing =
                ProviderResponseTiming(
                    totalMillis = response.totalDuration().nanosToMillis(),
                    promptEvaluationMillis = response.promptEvalDuration().nanosToMillis(),
                    generationMillis = response.evalDuration().nanosToMillis(),
                ),
            metadata =
                ProviderResponseMetadata(
                    rawFinishReason = rawFinishReason,
                    createdAtIso = response.createdAt().toString(),
                    requestId = requestId,
                    sequence = sequence,
                ),
        )
    }

    private fun normalizeFinishReason(rawFinishReason: String?): ProviderFinishReason? {
        if (rawFinishReason == null) return null
        return when (rawFinishReason.lowercase()) {
            "stop", "completed", "complete" -> ProviderFinishReason.STOP
            "tool_calls", "tool_call", "function_call" -> ProviderFinishReason.TOOL_CALLS
            "length", "max_tokens", "max_output_tokens" -> ProviderFinishReason.LENGTH
            "refusal" -> ProviderFinishReason.REFUSAL
            "content_filter", "content_filtered" -> ProviderFinishReason.CONTENT_FILTER
            "cancelled", "canceled" -> ProviderFinishReason.CANCELLED
            "error", "failed" -> ProviderFinishReason.ERROR
            else -> ProviderFinishReason.UNKNOWN
        }
    }

    private fun refusalSummary(metadata: ChatGenerationMetadata?): String? =
        metadata
            ?.contentFilters
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(", ")

    private fun fallbackCallId(
        requestId: String?,
        round: Int?,
        sequence: Int?,
        index: Int,
    ): String =
        listOf(requestId ?: "request", round ?: 0, sequence ?: 0, index)
            .joinToString(separator = "-", prefix = "fallback-tool-call-")

    private fun Long?.nanosToMillis(): Long? = this?.div(NANOS_PER_MILLI)

    private const val NANOS_PER_MILLI = 1_000_000L
}
