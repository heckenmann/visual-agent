package de.heckenmann.visualagent.ui.conversation.live

import de.heckenmann.visualagent.model.ToolLiveState
import de.heckenmann.visualagent.protocol.ToolActivity
import de.heckenmann.visualagent.protocol.ToolActivityPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConversationToolLiveStateTest {
    @Test
    fun `reopened conversation restores a snapshot but a racing completion prevents resurrection`() {
        val state = ConversationToolLiveState()
        val frame =
            ToolActivity(
                "sleep",
                "request",
                ToolActivityPhase.STARTED,
                executionId = "execution",
                liveState = ToolLiveState("execution", 1, "Waiting"),
            )
        state.accept(frame)
        assertEquals(
            "Waiting",
            state.snapshots.values
                .single()
                .status,
        )
        state.accept(frame.copy(phase = ToolActivityPhase.UPDATED, liveState = frame.liveState!!.copy(revision = 2, status = "Current")))
        state.accept(frame)
        assertEquals(
            "Current",
            state.snapshots.values
                .single()
                .status,
        )
        state.clear()
        state.accept(frame.copy(phase = ToolActivityPhase.FINISHED, liveState = frame.liveState!!.copy(revision = 3, terminal = true)))
        state.accept(frame)
        assertTrue(state.snapshots.isEmpty())
    }

    @Test
    fun `updates require a start and ignore stale revisions terminal frames and cleared calls`() {
        val state = ConversationToolLiveState()
        val frame =
            ToolActivity(
                "sleep",
                "request",
                ToolActivityPhase.UPDATED,
                providerToolCallId = "call",
                liveState = ToolLiveState("execution", 1, "Waiting"),
            )
        state.accept(frame)
        assertTrue(state.snapshots.isEmpty())
        state.accept(frame.copy(phase = ToolActivityPhase.STARTED, liveState = null))
        state.accept(frame)
        state.accept(frame.copy(liveState = frame.liveState!!.copy(revision = 0, status = "Old")))
        assertEquals(
            "Waiting",
            state.snapshots.values
                .single()
                .status,
        )
        state.accept(frame.copy(phase = ToolActivityPhase.FINISHED, liveState = frame.liveState!!.copy(revision = 2, terminal = true)))
        state.accept(frame.copy(liveState = frame.liveState!!.copy(revision = 3, status = "Late")))
        assertTrue(
            state.snapshots.values
                .single()
                .terminal,
        )
        val generationBeforeClear = state.generation
        state.clear()
        state.accept(frame.copy(phase = ToolActivityPhase.STARTED), generationBeforeClear)
        state.accept(frame)
        assertTrue(state.snapshots.isEmpty())
    }

    @Test
    fun `parallel calls remain isolated and keys agree with persisted metadata`() {
        val state = ConversationToolLiveState()
        val first =
            ToolActivity("sleep", "r", ToolActivityPhase.STARTED, providerToolCallId = "a", parentAssistantTurnId = "turn", sequence = 0)
        val second = first.copy(providerToolCallId = "b", sequence = 1)
        listOf(first, second).forEach { state.accept(it) }
        listOf(first, second).forEachIndexed { index, event ->
            state.accept(event.copy(phase = ToolActivityPhase.UPDATED, liveState = ToolLiveState("execution-$index", 1, "$index")))
        }
        assertEquals(2, state.snapshots.size)
        assertEquals(
            toolLiveKey(first),
            toolLiveKey("""{"toolId":"sleep","requestId":"r","providerToolCallId":"a","parentAssistantTurnId":"turn","sequence":0}"""),
        )
        assertEquals(2, remainingToolSeconds(2000, 1))
        assertEquals(0, remainingToolSeconds(2000, 2001))
    }
}
