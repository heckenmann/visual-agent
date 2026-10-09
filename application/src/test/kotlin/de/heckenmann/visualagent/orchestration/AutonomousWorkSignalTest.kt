package de.heckenmann.visualagent.orchestration

import reactor.test.StepVerifier
import kotlin.test.Test

class AutonomousWorkSignalTest {
    @Test
    fun `many signals retain only the latest pickup request`() {
        val signal = AutonomousWorkSignal()
        repeat(100) { signal.signal() }
        StepVerifier.create(signal.events().take(1)).expectNext(100L).verifyComplete()
    }
}
