package de.heckenmann.visualagent.orchestration

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertNull

class AutonomousWorkSignalTest {
    @Test
    fun `many signals while idle require one pickup pass`() =
        runBlocking {
            val signal = AutonomousWorkSignal()

            repeat(100) { signal.signal() }

            withTimeout(50) { signal.await() }
            assertNull(withTimeoutOrNull(50) { signal.await() })
        }
}
