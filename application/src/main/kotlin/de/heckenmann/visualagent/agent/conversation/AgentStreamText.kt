package de.heckenmann.visualagent.agent.conversation

/**
 * Appends one provider stream part and restores a missing sentence boundary when the provider
 * split two complete sentences across chunks without whitespace.
 *
 * @param target Collected response text
 * @param part Incoming provider stream part
 * @return The exact text appended to [target]
 */
internal fun appendStreamPart(
    target: StringBuilder,
    part: String,
): String {
    if (part.isEmpty()) return part
    val separator = if (needsSentenceSeparator(target, part)) "\n" else ""
    val appended = separator + part
    target.append(appended)
    return appended
}

private fun needsSentenceSeparator(
    target: StringBuilder,
    part: String,
): Boolean {
    if (target.isEmpty() || part.first().isWhitespace()) return false
    if (target.toString().trimEnd().endsWith("</think>") && part.trimStart().startsWith("<think>")) return true
    var previousIndex = target.length - 1
    while (previousIndex >= 0 && target[previousIndex].isWhitespace()) previousIndex--
    if (previousIndex < 0) return false
    val previousLast = target[previousIndex]
    if (previousLast != '.' && previousLast != '!' && previousLast != '?') return false
    val nextCharacter = part.firstOrNull { !it.isWhitespace() && it != '"' && it != '\'' }
    return nextCharacter?.isUpperCase() == true
}
