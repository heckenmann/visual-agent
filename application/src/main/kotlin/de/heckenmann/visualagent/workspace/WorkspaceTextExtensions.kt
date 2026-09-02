package de.heckenmann.visualagent.workspace

/** Returns a compact text excerpt around the supplied character index. */
internal fun String.snippet(index: Int): String {
    val start = (index - 80).coerceAtLeast(0)
    val end = (index + 160).coerceAtMost(length)
    return substring(start, end).replace(Regex("\\s+"), " ").trim()
}
