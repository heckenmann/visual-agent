package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.agent.ProviderResponseMetadata
import de.heckenmann.visualagent.agent.ProviderTurnResponse

/** Accumulates structured provider data emitted across streaming chunks. */
internal object ProviderTurnAccumulator {
    /**
     * Merge a later streaming chunk into the accumulated provider turn.
     *
     * Provider-designated reasoning summaries are delta-based and therefore
     * accumulated, while non-summary reasoning is never combined.
     */
    fun merge(
        current: ProviderTurnResponse?,
        next: ProviderTurnResponse,
    ): ProviderTurnResponse {
        if (current == null) return next
        val reasoning =
            when {
                current.reasoningIsSummary && next.reasoningIsSummary -> current.reasoning.orEmpty() + next.reasoning.orEmpty()
                next.reasoningIsSummary -> next.reasoning
                current.reasoningIsSummary -> current.reasoning
                else -> next.reasoning ?: current.reasoning
            }
        return current.copy(
            model = next.model.ifBlank { current.model },
            content = current.content + next.content,
            reasoning = reasoning?.takeIf(String::isNotBlank),
            reasoningIsSummary = current.reasoningIsSummary || next.reasoningIsSummary,
            toolCalls = next.toolCalls.ifEmpty { current.toolCalls },
            finishReason = next.finishReason ?: current.finishReason,
            refusal = next.refusal ?: current.refusal,
            usage = next.usage ?: current.usage,
            timing = next.timing ?: current.timing,
            metadata = next.metadata.takeUnless { it == ProviderResponseMetadata() } ?: current.metadata,
        )
    }
}
