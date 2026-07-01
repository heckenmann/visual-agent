package de.heckenmann.visualagent.ui.compose

import de.heckenmann.visualagent.agent.ToolResult
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ActivityIndicatorTest {
    @Test
    fun `totalActive is zero by default`() {
        val state = InFlightState()
        assertEquals(0, state.totalActive)
    }

    @Test
    fun `totalActive sums streaming, agents, tools and settings flag`() {
        val state =
            InFlightState(
                streamingRequestIds = setOf("req-1", "req-2"),
                runningAgentIds = setOf("agent-1"),
                pendingToolIds = setOf("file:read", "terminal", "history"),
                settingsLoading = true,
            )
        assertEquals(7, state.totalActive)
    }

    @Test
    fun `totalActive counts settings as exactly one`() {
        val state = InFlightState(settingsLoading = true)
        assertEquals(1, state.totalActive)
    }

    @Test
    fun `describe returns Idle when nothing is in flight`() {
        val state = InFlightState()
        assertEquals("Idle", state.describe())
    }

    @Test
    fun `describe enumerates each category in fixed order`() {
        val state =
            InFlightState(
                streamingRequestIds = setOf("req-1"),
                runningAgentIds = setOf("agent-1", "agent-2"),
                pendingToolIds = setOf("file:read", "terminal", "history"),
                settingsLoading = true,
            )
        val description = state.describe()
        assertTrue(description.contains("1 chat stream"))
        assertTrue(description.contains("2 sub-agents"))
        assertTrue(description.contains("3 tool calls"))
        assertTrue(description.contains("refreshing settings"))
    }

    @Test
    fun `describe uses singular forms for single counts`() {
        val state =
            InFlightState(
                runningAgentIds = setOf("agent-1"),
                pendingToolIds = setOf("file:read"),
            )
        val description = state.describe()
        assertTrue(description.contains("1 sub-agent"))
        assertTrue(description.contains("1 tool call"))
        assertTrue(!description.contains("sub-agents"))
        assertTrue(!description.contains("tool calls"))
    }

    @Test
    fun `holder markStreamStart and markStreamEnd update state`() {
        val holder = InFlightStateHolder()
        holder.markStreamStart("req-1")
        assertEquals(1, holder.state.value.totalActive)
        holder.markStreamStart("req-1")
        assertEquals(1, holder.state.value.totalActive)
        holder.markStreamEnd("req-1")
        assertEquals(0, holder.state.value.totalActive)
    }

    @Test
    fun `holder markStreamEnd is a no-op for unknown request id`() {
        val holder = InFlightStateHolder()
        holder.markStreamStart("req-1")
        holder.markStreamEnd("never-seen")
        assertEquals(1, holder.state.value.totalActive)
    }

    @Test
    fun `holder markAgentStart and markAgentEnd update state`() {
        val holder = InFlightStateHolder()
        holder.markAgentStart("agent-1")
        holder.markAgentStart("agent-2")
        assertEquals(2, holder.state.value.totalActive)
        holder.markAgentEnd("agent-1")
        assertEquals(setOf("agent-2"), holder.state.value.runningAgentIds)
    }

    @Test
    fun `holder markAgentEnd is a no-op for unknown agent id`() {
        val holder = InFlightStateHolder()
        holder.markAgentStart("agent-1")
        holder.markAgentEnd("never-seen")
        assertEquals(1, holder.state.value.totalActive)
    }

    @Test
    fun `holder setSettingsLoading toggles flag`() {
        val holder = InFlightStateHolder()
        holder.setSettingsLoading(true)
        assertEquals(true, holder.state.value.settingsLoading)
        holder.setSettingsLoading(true)
        assertEquals(true, holder.state.value.settingsLoading)
        holder.setSettingsLoading(false)
        assertEquals(false, holder.state.value.settingsLoading)
    }

    @Test
    fun `onToolEvent STARTED adds tool to pending tools`() {
        val holder = InFlightStateHolder()
        holder.onToolEvent(toolEvent(toolId = "file:read", phase = ToolCallPhase.STARTED))
        assertEquals(setOf("file:read"), holder.state.value.pendingToolIds)
    }

    @Test
    fun `onToolEvent FINISHED removes tool from pending tools`() {
        val holder = InFlightStateHolder()
        val event = toolEvent(toolId = "file:read", phase = ToolCallPhase.STARTED)
        holder.onToolEvent(event)
        holder.onToolEvent(event.copy(phase = ToolCallPhase.FINISHED))
        assertEquals(emptySet(), holder.state.value.pendingToolIds)
    }

    @Test
    fun `onToolEvent FINISHED without STARTED is ignored`() {
        val holder = InFlightStateHolder()
        holder.onToolEvent(
            toolEvent(toolId = "file:read", phase = ToolCallPhase.FINISHED, requestId = "ghost"),
        )
        assertEquals(emptySet(), holder.state.value.pendingToolIds)
    }

    @Test
    fun `onToolEvent STARTED with same key is idempotent`() {
        val holder = InFlightStateHolder()
        val event = toolEvent(toolId = "file:read", phase = ToolCallPhase.STARTED)
        holder.onToolEvent(event)
        holder.onToolEvent(event)
        holder.onToolEvent(event.copy(phase = ToolCallPhase.FINISHED))
        assertEquals(emptySet(), holder.state.value.pendingToolIds)
    }

    @Test
    fun `onToolEvent concurrent tool calls with different requestIds stay distinct`() {
        val holder = InFlightStateHolder()
        holder.onToolEvent(
            toolEvent(toolId = "file:read", phase = ToolCallPhase.STARTED, requestId = "r1"),
        )
        holder.onToolEvent(
            toolEvent(toolId = "file:read", phase = ToolCallPhase.STARTED, requestId = "r2"),
        )
        assertEquals(1, holder.state.value.pendingToolIds.size)
        holder.onToolEvent(
            toolEvent(
                toolId = "file:read",
                phase = ToolCallPhase.FINISHED,
                requestId = "r1",
            ),
        )
        assertEquals(setOf("file:read"), holder.state.value.pendingToolIds)
        assertNotEquals(emptySet(), holder.state.value.pendingToolIds)
        holder.onToolEvent(
            toolEvent(
                toolId = "file:read",
                phase = ToolCallPhase.FINISHED,
                requestId = "r2",
            ),
        )
        assertEquals(emptySet(), holder.state.value.pendingToolIds)
    }

    @Test
    fun `stream agents and tools combine in totalActive`() {
        val holder = InFlightStateHolder()
        holder.markStreamStart("req-1")
        holder.markStreamStart("req-2")
        holder.markAgentStart("agent-1")
        holder.setSettingsLoading(true)
        holder.onToolEvent(toolEvent(toolId = "file:read", phase = ToolCallPhase.STARTED))
        assertEquals(5, holder.state.value.totalActive)
        val description = holder.state.value.describe()
        assertTrue(description.contains("2 chat streams"))
        assertTrue(description.contains("1 sub-agent"))
        assertTrue(description.contains("1 tool call"))
        assertTrue(description.contains("refreshing settings"))
    }

    private fun toolEvent(
        toolId: String,
        phase: ToolCallPhase,
        requestId: String = "req-0",
    ): ToolCallEvent {
        val now = Instant.parse("2024-01-01T00:00:00Z")
        return ToolCallEvent(
            toolId = toolId,
            functionName = toolId,
            phase = phase,
            inputJson = "{}",
            context = mapOf("requestId" to requestId),
            result = ToolResult(toolId = toolId, success = true, content = "ok"),
            startedAtUtc = now,
            finishedAtUtc = now,
            durationMillis = 0,
        )
    }
}
