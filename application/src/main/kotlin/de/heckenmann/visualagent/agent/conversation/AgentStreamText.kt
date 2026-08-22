package de.heckenmann.visualagent.agent.conversation

/**
 * Appends one provider stream part without changing provider-supplied content.
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
    target.append(part)
    return part
}
