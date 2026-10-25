package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDownloadPhase
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.model.ToolLiveState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceDownloadLiveReporterTest {
    @Test
    fun `progress replaces the current value without accumulating log entries`() {
        val states = mutableListOf<ToolLiveState>()
        val reporter = WorkspaceDownloadLiveReporter(states::add)
        reporter.phase(ToolDownloadPhase.STARTED)
        repeat(100) { reporter.progress(it.toLong(), 100) }
        assertEquals(99L, states.last().progress!!.current)
        assertTrue(states.all { it.logs.isEmpty() })
    }

    @Test
    fun `in-flight progress does not overwrite canonical pause and resume phases`() {
        val states = mutableListOf<ToolLiveState>()
        val reporter = WorkspaceDownloadLiveReporter(states::add)
        reporter.phase(ToolDownloadPhase.STARTED)
        reporter.progress(1, 10)
        reporter.phase(ToolDownloadPhase.PAUSED)
        reporter.progress(2, 10)
        assertEquals("Paused", states.last().status)
        assertEquals(2L, states.last().progress!!.current)
        reporter.phase(ToolDownloadPhase.RESUMED)
        reporter.progress(3, 10)
        assertEquals("Downloading", states.last().status)
        reporter.phase(ToolDownloadPhase.CANCELLED)
        assertTrue(reporter.isCancelled)
        assertEquals("Cancelled", states.last().status)
    }

    @Test
    fun `live final labels use canonical failure codes`() {
        assertEquals("Cancelled", liveTerminalStatus(ToolResult("sleep", false, "", "TOOL_CANCELLED: stopped")))
        assertEquals("Timed out", liveTerminalStatus(ToolResult("sleep", false, "", "TOOL_TIMEOUT: expired")))
        assertEquals("Failed", liveTerminalStatus(ToolResult("sleep", false, "", "TOOL_FAILURE: failed")))
        assertEquals("Completed", liveTerminalStatus(ToolResult("sleep", true, "done")))
    }
}
