package de.heckenmann.visualagent.agent.javascript

/** Tracks the serialized result budget while traversing guest values. */
internal class JavaScriptResultBudget(
    private val maxCharacters: Int,
    private val limitMessage: String = "JavaScript result size limit exceeded",
) {
    private var usedCharacters = 0

    /** Reserve output characters or fail before materializing more host data. */
    fun consume(characters: Int) {
        if (characters < 0 || usedCharacters > maxCharacters - characters) {
            throw JavaScriptExecutionException(JavaScriptErrorCategory.LIMIT_EXCEEDED, limitMessage)
        }
        usedCharacters += characters
    }

    /** Return the number of array elements that can still be traversed safely. */
    fun remainingElements(): Int = (maxCharacters - usedCharacters).coerceAtLeast(0)
}
