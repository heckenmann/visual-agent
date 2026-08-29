package de.heckenmann.visualagent.ui.todo

import java.text.BreakIterator
import java.util.Locale

/** Converts streamed response output into the single visual line used by todo rows. */
internal fun todoStreamingLine(response: String): String = response.replace(Regex("\\s+"), " ").trim()

/**
 * Returns the newest grapheme-safe suffix that fits within the supplied width.
 *
 * @param text Complete single-line response text
 * @param availableWidthPx Available text width in pixels
 * @param measureWidthPx Measures the candidate text width in pixels
 */
internal fun fittedTextSuffix(
    text: String,
    availableWidthPx: Int,
    measureWidthPx: (String) -> Int,
): String {
    if (text.isEmpty() || availableWidthPx <= 0 || measureWidthPx(text) <= availableWidthPx) return text
    val boundaries = graphemeBoundaries(text)
    var firstCandidate = 0
    var lastCandidate = boundaries.lastIndex
    while (firstCandidate < lastCandidate) {
        val candidateIndex = (firstCandidate + lastCandidate) / 2
        if (measureWidthPx(text.substring(boundaries[candidateIndex])) <= availableWidthPx) {
            lastCandidate = candidateIndex
        } else {
            firstCandidate = candidateIndex + 1
        }
    }
    return text.substring(boundaries[firstCandidate])
}

private fun graphemeBoundaries(text: String): IntArray {
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    iterator.setText(text)
    return buildList {
        var boundary = iterator.first()
        while (boundary != BreakIterator.DONE) {
            add(boundary)
            boundary = iterator.next()
        }
    }.toIntArray()
}
