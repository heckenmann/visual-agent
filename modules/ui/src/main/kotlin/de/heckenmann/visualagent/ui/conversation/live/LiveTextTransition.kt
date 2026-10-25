package de.heckenmann.visualagent.ui.conversation.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Composition-owned transition with one replaceable pending value, never an animation queue. */
internal class LiveTextTransition(
    initial: String,
) {
    var previous by mutableStateOf(initial)
        private set
    var current by mutableStateOf(initial)
        private set
    var running by mutableStateOf(false)
        private set
    var revision by mutableLongStateOf(0)
        private set
    private var pending by mutableStateOf<String?>(null)
    private var terminalSeen = false

    /** Latest accepted text for accessibility, independent of the visual transition's intermediate frame. */
    val latest: String get() = pending ?: current

    /** Keeps the newest update; terminal state cannot be displaced by a later running update. */
    fun offer(
        value: String,
        terminal: Boolean,
    ) {
        if (terminalSeen && !terminal) return
        terminalSeen = terminalSeen || terminal
        if (!running && current.isEmpty()) {
            previous = value
            current = value
        } else if (running) {
            pending = value.takeUnless { it == current }
        } else if (value != current) {
            previous = current
            current = value
            running = true
            revision++
        }
    }

    /** Finishes one visual transition and advances directly to the most recent pending value. */
    fun complete() {
        previous = current
        running = false
        val next = pending
        pending = null
        if (next != null) offer(next, terminalSeen)
    }

    /** Honors disabled motion immediately, including the latest pending value. */
    fun settle() {
        current = pending ?: current
        previous = current
        pending = null
        running = false
    }
}
