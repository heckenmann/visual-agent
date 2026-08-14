package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.knowledge.PreferenceStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests persistence and cooperative waiting of the shared execution gates. */
class SubAgentExecutionControlTest {
    @Test
    fun `global and individual pause state persists independently`() {
        val preferences = MemoryPreferenceStore()
        val control = SubAgentExecutionControl(preferences)

        control.pauseAgent("agent-1")
        control.pauseAll()
        assertEquals(SubAgentExecutionState.PAUSED, control.status("agent-1").effectiveState)
        assertEquals(SubAgentPauseReason.GLOBAL_AND_INDIVIDUAL, control.status("agent-1").pauseReason)

        val restored = SubAgentExecutionControl(preferences)
        assertTrue(restored.isGloballyPaused())
        assertTrue(restored.isAgentPaused("agent-1"))
        restored.resumeAll()
        assertEquals(SubAgentExecutionState.PAUSED, restored.status("agent-1").effectiveState)
        assertEquals(SubAgentPauseReason.INDIVIDUAL, restored.status("agent-1").pauseReason)
    }

    @Test
    fun `await resumes only after both gates are running`() =
        runBlocking {
            val control = SubAgentExecutionControl(MemoryPreferenceStore())
            control.pauseAll()
            control.pauseAgent("agent-1")
            val released = CompletableDeferred<Unit>()
            val waiting =
                async(Dispatchers.Default) {
                    control.awaitExecutionAllowed("agent-1")
                    released.complete(Unit)
                }

            delay(50)
            assertFalse(released.isCompleted)
            control.resumeAgent("agent-1")
            delay(50)
            assertFalse(released.isCompleted)
            control.resumeAll()
            withTimeout(1_000) { released.await() }
            waiting.await()
        }

    @Test
    fun `removing agent clears its individual gate`() {
        val control = SubAgentExecutionControl(MemoryPreferenceStore())
        control.pauseAgent("agent-1")
        control.removeAgent("agent-1")
        assertFalse(control.isAgentPaused("agent-1"))
    }

    @Test
    fun `async mutations persist execution state`() =
        runBlocking {
            val preferences = MemoryPreferenceStore()
            val control = SubAgentExecutionControl(preferences)

            control.pauseAllAsync()
            control.pauseAgentAsync("agent-1")

            val restored = SubAgentExecutionControl(preferences)
            assertEquals(SubAgentExecutionState.PAUSED, restored.status("agent-1").effectiveState)
            assertEquals(SubAgentPauseReason.GLOBAL_AND_INDIVIDUAL, restored.status("agent-1").pauseReason)

            control.resumeAgentAsync("agent-1")
            control.resumeAllAsync()
            assertTrue(control.isExecutionAllowed("agent-1"))
        }
}

private class MemoryPreferenceStore : PreferenceStore {
    private val values = mutableMapOf<String, String>()

    override fun getPreference(key: String): String? = values[key]

    override fun setPreference(
        key: String,
        value: String,
    ) {
        values[key] = value
    }
}
