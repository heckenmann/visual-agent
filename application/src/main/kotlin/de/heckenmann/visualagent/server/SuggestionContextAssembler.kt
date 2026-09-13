package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.Message
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator
import org.springframework.ai.tokenizer.TokenCountEstimator

/** Builds a bounded, latest-first provider context for idle follow-up suggestions. */
internal class SuggestionContextAssembler(
    private val tokenEstimator: TokenCountEstimator = JTokkitTokenCountEstimator(),
) {
    /**
     * Retains recent messages within the configured context limit after reserving fixed prompt and output tokens.
     *
     * The newest message is retained in truncated form when necessary so the completed assistant answer
     * always remains available for suggestion generation.
     */
    fun assemble(
        history: List<Message>,
        systemInstruction: String,
        finalRequest: String,
        contextLength: Int,
    ): List<Message> {
        val budget = inputBudget(systemInstruction, finalRequest, contextLength)
        val retained = ArrayDeque<Message>()
        var remaining = budget
        history.asReversed().forEach { message ->
            val estimate = tokenEstimator.estimate(message.content)
            when {
                estimate <= remaining -> {
                    retained.addFirst(message)
                    remaining -= estimate
                }
                retained.isEmpty() -> {
                    truncate(message, remaining)?.let(retained::addFirst)
                    remaining = 0
                }
                else -> return retained.toList()
            }
        }
        return retained.toList()
    }

    private fun inputBudget(
        systemInstruction: String,
        finalRequest: String,
        contextLength: Int,
    ): Int =
        (
            contextLength.coerceAtLeast(MIN_CONTEXT_TOKENS) -
                tokenEstimator.estimate(systemInstruction) -
                tokenEstimator.estimate(finalRequest) -
                RESERVED_OUTPUT_TOKENS
        ).coerceAtLeast(MIN_HISTORY_TOKENS)

    private fun truncate(
        message: Message,
        tokenBudget: Int,
    ): Message? {
        if (tokenBudget <= 0 || message.content.isBlank()) return null
        var low = 1
        var high = message.content.length
        var best = ""
        while (low <= high) {
            val middle = (low + high) / 2
            val candidate = message.content.take(middle) + "…"
            if (tokenEstimator.estimate(candidate) <= tokenBudget) {
                best = candidate
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return best.takeIf(String::isNotBlank)?.let { message.copy(content = it) }
    }

    private companion object {
        const val RESERVED_OUTPUT_TOKENS = 256
        const val MIN_CONTEXT_TOKENS = 1_024
        const val MIN_HISTORY_TOKENS = 128
    }
}
