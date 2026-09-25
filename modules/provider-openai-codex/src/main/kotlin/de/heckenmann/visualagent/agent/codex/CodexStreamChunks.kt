package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.StreamSectionBoundary

/** Splits a complete provider message into small Unicode-safe presentation chunks. */
internal fun String.simulatedChunks(): List<String> {
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < length) {
        val count = codePointCount(start, length)
        val end = offsetByCodePoints(start, minOf(CHUNK_CODE_POINT_COUNT, count))
        chunks += substring(start, end)
        start = end
    }
    return chunks
}

/** One pending assistant delta associated with its native Codex item identity. */
internal data class CodexAssistantTextDelta(
    val text: String,
    val itemId: String?,
)

/** Final chunk group from one pending Codex item. */
internal data class CodexAssistantDeltaBatch(
    val itemId: String?,
    val chunks: List<String>,
    val animate: Boolean,
)

/** Buffers Codex deltas so section boundaries are inserted only when item IDs change. */
internal class CodexAssistantDeltaBuffer {
    private var pending: CodexAssistantTextDelta? = null
    private var previousVisibleTail = ""
    private var hasVisibleText = false
    private var receivedMultipleDeltas = false

    /** Accepts one provider delta and returns the previous pending delta when it becomes complete. */
    fun accept(
        text: String,
        itemId: String?,
    ): CodexAssistantTextDelta? {
        if (text.isEmpty()) return null
        val previous =
            pending ?: run {
                pending = CodexAssistantTextDelta(text, itemId)
                return null
            }
        record(previous.text)
        receivedMultipleDeltas = true
        val boundary =
            if (previous.itemId != itemId && hasVisibleText) {
                StreamSectionBoundary.prefix(previousVisibleTail, text, hasVisibleText)
            } else {
                ""
            }
        pending = CodexAssistantTextDelta(boundary + text, itemId)
        return previous
    }

    /** Completes the pending item, simulating small chunks only for a single native delta. */
    fun complete(): CodexAssistantDeltaBatch? {
        val last = pending ?: return null
        pending = null
        val chunks = if (receivedMultipleDeltas) listOf(last.text) else last.text.simulatedChunks()
        return CodexAssistantDeltaBatch(last.itemId, chunks, animate = !receivedMultipleDeltas)
    }

    private fun record(text: String) {
        previousVisibleTail = (previousVisibleTail + text).takeLast(4)
        hasVisibleText = hasVisibleText || text.isNotBlank()
    }
}

private const val CHUNK_CODE_POINT_COUNT = 3
