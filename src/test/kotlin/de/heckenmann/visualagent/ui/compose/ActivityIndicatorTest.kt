package de.heckenmann.visualagent.ui.compose

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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
    fun `holder markAgentStart and markAgentEnd update state`() {
        val holder = InFlightStateHolder()
        holder.markAgentStart("agent-1")
        holder.markAgentStart("agent-2")
        assertEquals(2, holder.state.value.totalActive)
        holder.markAgentEnd("agent-1")
        assertEquals(setOf("agent-2"), holder.state.value.runningAgentIds)
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
}
