package de.heckenmann.visualagent.orchestration

import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class AutonomousWorkSignalTest {
    @Test
    fun `many signals make a pickup pass available`() =
        runBlocking {
            val signal = AutonomousWorkSignal()

            repeat(100) { signal.signal() }

            signal.await()
        }
}
