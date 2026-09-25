package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.StreamSectionBoundary

/** One assistant delta associated with its native Codex item identity. */
internal data class CodexAssistantTextDelta(
    val text: String,
    val itemId: String?,
)

/** Tracks Codex item boundaries while forwarding each native delta immediately. */
internal class CodexAssistantDeltaBuffer {
    private var previousItemId: String? = null
    private var previousVisibleTail = ""
    private var hasVisibleText = false

    /** Returns the received delta with a section boundary when its item changes. */
    fun accept(
        text: String,
        itemId: String?,
    ): CodexAssistantTextDelta? {
        if (text.isEmpty()) return null
        val boundary =
            if (previousItemId != itemId && hasVisibleText) {
                StreamSectionBoundary.prefix(previousVisibleTail, text, hasVisibleText)
            } else {
                ""
            }
        val visibleText = boundary + text
        previousItemId = itemId
        record(visibleText)
        return CodexAssistantTextDelta(visibleText, itemId)
    }

    private fun record(text: String) {
        previousVisibleTail = (previousVisibleTail + text).takeLast(4)
        hasVisibleText = hasVisibleText || text.isNotBlank()
    }
}
