package de.heckenmann.visualagent.agent

/** Adds a Markdown block boundary only between separate, visible assistant sections. */
object StreamSectionBoundary {
    /**
     * Returns the prefix needed to separate the next visible section from the previous one.
     *
     * @param previousTail Last characters emitted from the previous section
     * @param nextSection First content from the next section
     * @param hasPreviousVisibleText Whether the previous section contains visible text
     * @return A minimal newline prefix or an empty string when existing spacing is sufficient
     */
    fun prefix(
        previousTail: CharSequence,
        nextSection: CharSequence,
        hasPreviousVisibleText: Boolean = previousTail.isNotBlank(),
    ): String {
        if (!hasPreviousVisibleText || nextSection.isBlank() || nextSection.first().isWhitespace()) return ""
        return when (trailingLineBreaks(previousTail)) {
            0 -> "\n\n"
            1 -> "\n"
            else -> ""
        }
    }

    /**
     * Adds a required boundary to a completed response while keeping structured and plain content aligned.
     *
     * @param previousText Visible content already emitted by the initial stream
     * @param response Final tool response
     * @return The response with a shared boundary in both content representations when needed
     */
    fun separateResponse(
        previousText: CharSequence,
        response: ChatResponse,
    ): ChatResponse {
        val prefix = prefix(previousText, response.message.content)
        if (prefix.isEmpty()) return response
        return response.copy(
            message = response.message.copy(content = prefix + response.message.content),
            providerTurn = response.providerTurn?.let { turn -> turn.copy(content = prefix + turn.content) },
        )
    }

    private fun trailingLineBreaks(text: CharSequence): Int {
        var index = text.length - 1
        var count = 0
        while (index >= 0) {
            when (text[index]) {
                '\n' -> {
                    count++
                    if (index > 0 && text[index - 1] == '\r') index--
                }
                '\r' -> count++
                else -> return count
            }
            index--
        }
        return count
    }
}
