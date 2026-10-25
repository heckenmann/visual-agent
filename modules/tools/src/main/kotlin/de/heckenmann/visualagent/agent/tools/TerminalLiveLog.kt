package de.heckenmann.visualagent.agent.tools

/** Invocation-owned logical lines, independent of arbitrary Reader chunk boundaries. */
internal class TerminalLiveLog {
    private val completed = ArrayDeque<String>()
    private val partial = StringBuilder()
    private var afterCarriageReturn = false

    /** Keeps partial output visible immediately while coalescing CRLF across successive reads. */
    fun append(
        buffer: CharArray,
        count: Int,
    ) {
        repeat(count) { index ->
            val char = buffer[index]
            if (char == '\n' && afterCarriageReturn) {
                afterCarriageReturn = false
            } else {
                afterCarriageReturn = char == '\r'
                if (char == '\n' || char == '\r') {
                    completed.addLast(partial.toString())
                    partial.setLength(0)
                    if (completed.size > 40) completed.removeFirst()
                } else if (partial.length < 500) {
                    partial.append(char)
                }
            }
        }
    }

    /** Returns retained complete lines and the current unfinished line without adding fake newlines. */
    fun snapshot(): List<String> = (completed.toList() + listOfNotNull(partial.toString().takeIf { it.isNotEmpty() })).takeLast(40)
}
