package de.heckenmann.visualagent.agent.text

/** Removes provider-internal reasoning markup before text is reused in a provider request. */
internal object ThinkingMarkup {
    /** Returns content without complete, partial, or stray thinking markup. */
    fun remove(content: String): String =
        content
            .replace(THINKING_BLOCK_PATTERN, "")
            .replace(UNFINISHED_THINKING_BLOCK_PATTERN, "")
            .replace(CLOSE_THINKING_TAG_PATTERN, "")

    /** Returns whether content contains an opening or closing thinking tag. */
    fun isPresent(content: String): Boolean =
        OPEN_THINKING_TAG_PATTERN.containsMatchIn(content) || CLOSE_THINKING_TAG_PATTERN.containsMatchIn(content)

    private val OPEN_THINKING_TAG_PATTERN = Regex("(?i)<think>")
    private val THINKING_BLOCK_PATTERN = Regex("(?is)<think>.*?</think>")
    private val UNFINISHED_THINKING_BLOCK_PATTERN = Regex("(?is)<think>.*$")
    private val CLOSE_THINKING_TAG_PATTERN = Regex("(?i)</think>")
}
