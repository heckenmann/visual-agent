package de.heckenmann.visualagent.orchestration

import kotlinx.coroutines.channels.Channel

/** Conflates state changes that may make autonomous work executable. */
internal class AutonomousWorkSignal {
    private val signals = Channel<Unit>(Channel.CONFLATED)

    /** Requests a non-blocking pickup pass. */
    fun signal() {
        signals.trySend(Unit)
    }

    /** Waits until at least one pickup pass was requested. */
    suspend fun await() {
        signals.receive()
    }
}
