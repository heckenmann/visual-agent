package de.heckenmann.visualagent.ui.conversation.live

import de.heckenmann.visualagent.model.ToolLiveState
import de.heckenmann.visualagent.protocol.ToolActivity
import de.heckenmann.visualagent.protocol.ToolActivityPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConversationInitialLiveSyncTest {
    @Test
    fun `newest update arriving before the initial snapshot wins`() {
        val state = ConversationToolLiveState()
        val sync = state.beginSnapshotSync()
        state.accept(event(ToolActivityPhase.UPDATED, 3, "Paused"))
        state.accept(event(ToolActivityPhase.UPDATED, 2, "Downloading"))
        assertTrue(state.snapshots.isEmpty())
        state.completeSnapshotSync(listOf(event(ToolActivityPhase.STARTED, 1, "Connecting")), sync)
        assertEquals(3L, state.snapshots.getValue("execution").revision)
        assertEquals("Paused", state.snapshots.getValue("execution").status)
    }

    @Test
    fun `completion and reset invalidate buffered updates and old snapshots`() {
        val state = ConversationToolLiveState()
        val sync = state.beginSnapshotSync()
        state.accept(event(ToolActivityPhase.UPDATED, 2, "Paused"))
        state.accept(event(ToolActivityPhase.FINISHED, 3, "Completed"))
        state.completeSnapshotSync(listOf(event(ToolActivityPhase.STARTED, 1, "Connecting")), sync)
        assertTrue(state.snapshots.isEmpty())
        state.clear()
        val resetSync = state.beginSnapshotSync()
        state.accept(event(ToolActivityPhase.UPDATED, 4, "Late"))
        state.clear()
        state.completeSnapshotSync(listOf(event(ToolActivityPhase.STARTED, 1, "Connecting")), resetSync)
        state.accept(event(ToolActivityPhase.UPDATED, 5, "Late again"))
        assertTrue(state.snapshots.isEmpty())
    }

    @Test
    fun `cancelled initial fetch discards updates that never received a start`() {
        val state = ConversationToolLiveState()
        val sync = state.beginSnapshotSync()
        state.accept(event(ToolActivityPhase.UPDATED, 2, "Paused"))
        state.cancelSnapshotSync(sync)
        state.accept(event(ToolActivityPhase.STARTED, 1, "Connecting"))
        assertEquals(1L, state.snapshots.getValue("execution").revision)
    }

    @Test
    fun `disposing an older subscription cannot discard the replacement subscriptions updates`() {
        val state = ConversationToolLiveState()
        val old = state.beginSnapshotSync()
        val current = state.beginSnapshotSync()
        state.accept(event(ToolActivityPhase.UPDATED, 2, "Paused"))
        state.cancelSnapshotSync(old)
        state.completeSnapshotSync(listOf(event(ToolActivityPhase.STARTED, 1, "Connecting")), old)
        state.completeSnapshotSync(listOf(event(ToolActivityPhase.STARTED, 1, "Connecting")), current)
        assertEquals("Paused", state.snapshots.getValue("execution").status)
    }

    private fun event(
        phase: ToolActivityPhase,
        revision: Long,
        status: String,
    ) = ToolActivity(
        "workspace:download",
        "request",
        phase,
        executionId = "execution",
        liveState = ToolLiveState("execution", revision, status, terminal = phase == ToolActivityPhase.FINISHED),
    )
}
