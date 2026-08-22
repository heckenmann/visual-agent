package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.AgentStatusCallbackAdapter
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.protocol.DownloadActivity
import de.heckenmann.visualagent.protocol.DownloadActivityStatus
import de.heckenmann.visualagent.workspace.WorkspaceDownloadEventBus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies translation of application activity events at the protocol boundary. */
class SpringActivityPortTest {
    @Test
    fun `tool lifecycle events preserve request metadata and outcome`() {
        val toolBus = ToolEventBus()
        val port = SpringActivityPort(toolBus, AgentStatusCallbackAdapter())
        val activities = mutableListOf<de.heckenmann.visualagent.protocol.ToolActivity>()
        val registration = port.addToolListener(activities::add)
        val now = Instant.now()

        toolBus.publish(
            ToolCallEvent(
                toolId = "todos",
                functionName = "todos",
                phase = ToolCallPhase.STARTED,
                inputJson = "{}",
                context = mapOf("requestId" to "request-1"),
                result = ToolResult("todos", success = true, content = "started"),
                startedAtUtc = now,
                finishedAtUtc = now,
                durationMillis = 0,
            ),
        )
        toolBus.publish(
            ToolCallEvent(
                toolId = "todos",
                functionName = "todos",
                phase = ToolCallPhase.FINISHED,
                inputJson = "{}",
                context = emptyMap(),
                result = ToolResult("todos", success = false, content = "failed"),
                startedAtUtc = now,
                finishedAtUtc = now,
                durationMillis = 1,
            ),
        )
        registration.close()

        assertEquals(2, activities.size)
        assertEquals("request-1", activities[0].requestId)
        assertEquals(de.heckenmann.visualagent.protocol.ToolActivityPhase.STARTED, activities[0].phase)
        assertEquals(true, activities[0].success)
        assertEquals(null, activities[1].requestId)
        assertEquals(de.heckenmann.visualagent.protocol.ToolActivityPhase.FINISHED, activities[1].phase)
        assertEquals(false, activities[1].success)
    }

    @Test
    fun `agent status events map busy and idle transitions`() {
        val callback = AgentStatusCallbackAdapter()
        val port = SpringActivityPort(ToolEventBus(), callback)
        val activities = mutableListOf<de.heckenmann.visualagent.protocol.AgentActivity>()
        val registration = port.addAgentListener(activities::add)

        callback.notify("agent-1", "STATUS:BUSY processing")
        callback.notify("agent-1", "STATUS:IDLE ready")
        callback.notify("agent-1", "CREATED")
        registration.close()

        assertEquals(
            listOf(
                de.heckenmann.visualagent.protocol.AgentActivity(
                    "agent-1",
                    de.heckenmann.visualagent.protocol.AgentActivityPhase.STARTED,
                ),
                de.heckenmann.visualagent.protocol.AgentActivity(
                    "agent-1",
                    de.heckenmann.visualagent.protocol.AgentActivityPhase.FINISHED,
                ),
            ),
            activities,
        )
    }

    @Test
    fun `download status events cross the activity boundary`() {
        val downloadBus = WorkspaceDownloadEventBus()
        val port = SpringActivityPort(ToolEventBus(), AgentStatusCallbackAdapter(), downloadBus)
        val activities = mutableListOf<DownloadActivity>()
        val registration = port.addDownloadListener(activities::add)

        downloadBus.publish(
            DownloadActivity(
                id = "download-1",
                relativePath = "downloads/report.pdf",
                status = DownloadActivityStatus.COMPLETED,
                downloadedBytes = 42,
                totalBytes = 42,
            ),
        )
        registration.close()

        assertEquals(1, activities.size)
        assertEquals(DownloadActivityStatus.COMPLETED, activities.single().status)
        assertEquals("downloads/report.pdf", activities.single().relativePath)
    }
}
