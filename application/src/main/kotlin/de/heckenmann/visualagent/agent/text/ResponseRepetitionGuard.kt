package de.heckenmann.visualagent.agent.text

/**
 * Detects malformed assistant output dominated by repeated phrase loops.
 */
internal object ResponseRepetitionGuard {
    /**
     * Returns whether the given content appears to be a repetition loop instead of a valid response.
     *
     * @param content Assistant output content
     * @return `true` when repeated sliding windows dominate the response
     */
    fun isRunawayRepetition(content: String): Boolean {
        if (content.length < 400) return false
        val windows =
            content
                .windowed(size = 24, step = 6, partialWindows = false)
                .filter { window ->
                    val normalized = window.lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }
                    normalized.isNotBlank()
                }
        if (windows.isEmpty()) return false
        val frequencies = windows.groupingBy { it }.eachCount()
        val maxRepeat = frequencies.values.maxOrNull() ?: 0
        return maxRepeat >= 12
    }
}
