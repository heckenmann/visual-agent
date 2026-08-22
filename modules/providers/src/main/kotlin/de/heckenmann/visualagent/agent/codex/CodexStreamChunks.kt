package de.heckenmann.visualagent.agent.codex

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

private const val CHUNK_CODE_POINT_COUNT = 3
