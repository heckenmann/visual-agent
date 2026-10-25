package de.heckenmann.visualagent.ui.conversation.live

/** Aligns words/graphemes without rewriting text; overly large inputs use native static rendering. */
internal fun liveTextPieces(
    previous: String,
    current: String,
): List<LiveTextPiece>? {
    val before =
        liveTokens
            .findAll(previous)
            .map { it.value }
            .take(MAX_LIVE_TEXT_TOKENS + 1)
            .toList()
    val after =
        liveTokens
            .findAll(current)
            .map { it.value }
            .take(MAX_LIVE_TEXT_TOKENS + 1)
            .toList()
    if (before.size > MAX_LIVE_TEXT_TOKENS || after.size > MAX_LIVE_TEXT_TOKENS) return null
    val lengths = Array(before.size + 1) { IntArray(after.size + 1) }
    for (i in before.indices.reversed()) {
        for (j in after.indices.reversed()) {
            lengths[i][j] = if (before[i] == after[j]) lengths[i + 1][j + 1] + 1 else maxOf(lengths[i + 1][j], lengths[i][j + 1])
        }
    }
    return buildList {
        val removed = mutableListOf<String>()
        val inserted = mutableListOf<String>()

        /** Pairs one replacement gap, preserving numeric place alignment when digit counts change. */
        fun flush() {
            val size = maxOf(removed.size, inserted.size)
            val numeric = (removed + inserted).all { it.codePoints().allMatch(Character::isDigit) }
            repeat(size) { index ->
                val oldIndex = if (numeric) index - (size - removed.size) else index
                val newIndex = if (numeric) index - (size - inserted.size) else index
                add(LiveTextPiece(removed.getOrElse(oldIndex) { "" }, inserted.getOrElse(newIndex) { "" }))
            }
            removed.clear()
            inserted.clear()
        }
        var i = 0
        var j = 0
        while (i < before.size || j < after.size) {
            when {
                i < before.size && j < after.size && before[i] == after[j] -> {
                    flush()
                    add(LiveTextPiece(before[i], after[j]))
                    i++
                    j++
                }
                i < before.size && (j == after.size || lengths[i + 1][j] >= lengths[i][j + 1]) -> removed.add(before[i++])
                else -> inserted.add(after[j++])
            }
        }
        flush()
    }
}

private const val MAX_LIVE_TEXT_TOKENS = 256
private val liveTokens = Regex("\\p{L}[\\p{L}\\p{M}]*|\\p{N}|\\X")
