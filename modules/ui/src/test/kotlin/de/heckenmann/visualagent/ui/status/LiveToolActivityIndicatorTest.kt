package de.heckenmann.visualagent.ui.status

import de.heckenmann.visualagent.protocol.ToolActivity
import de.heckenmann.visualagent.protocol.ToolActivityPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveToolActivityIndicatorTest {
    @Test
    fun `finishing one concurrent invocation keeps the other invocation active`() {
        val holder = InFlightStateHolder()
        val first = ToolActivity("terminal", "request", ToolActivityPhase.STARTED, executionId = "first")
        val second = first.copy(executionId = "second")
        holder.onToolEvent(first)
        holder.onToolEvent(second)
        holder.onToolEvent(first.copy(phase = ToolActivityPhase.UPDATED))
        holder.onToolEvent(first.copy(phase = ToolActivityPhase.FINISHED))
        assertEquals(setOf("terminal"), holder.state.value.pendingToolIds)
        holder.onToolEvent(second.copy(phase = ToolActivityPhase.FINISHED))
        assertTrue(
            holder.state.value.pendingToolIds
                .isEmpty(),
        )
    }
}
