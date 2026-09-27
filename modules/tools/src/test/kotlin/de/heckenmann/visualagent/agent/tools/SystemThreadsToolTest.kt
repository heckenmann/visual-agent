package de.heckenmann.visualagent.agent.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemThreadsToolTest {
    @Test
    fun `tool validates request and returns bounded thread details`() {
        var observed: ThreadDiagnosticsRequest? = null
        val tool =
            SystemThreadsTool(
                ThreadDiagnosticsProbe { request ->
                    observed = request
                    ThreadDiagnosticsResult(
                        action = request.action,
                        summary = ThreadSummary(3, 2, 4, mapOf("RUNNABLE" to 2, "WAITING" to 1), 0),
                        threads = listOf(ThreadEntry(7, "worker", "RUNNABLE", null, null, listOf("example.Work.run(Work.kt:10)"))),
                        matchingThreads = 1,
                        truncated = false,
                    )
                },
            )

        val invalidAction = tool.execute("""{"action":"kill"}""", emptyMap())
        val invalidState = tool.execute("""{"action":"dump","state":"SLEEPING"}""", emptyMap())
        val invalidLimit = tool.execute("""{"action":"dump","maxThreads":101}""", emptyMap())
        val result = tool.execute("""{"action":"dump","state":"RUNNABLE","maxThreads":1,"maxFrames":1}""", emptyMap())

        assertFalse(invalidAction.success)
        assertFalse(invalidState.success)
        assertFalse(invalidLimit.success)
        assertEquals(ThreadDiagnosticsRequest("dump", "RUNNABLE", 1, 1), observed)
        assertTrue(result.success)
        assertTrue(result.content.contains("\"runtimeScope\":\"visual-agent-server-jvm\""))
        assertTrue(result.content.contains("\"threadScope\":\"platform-only\""))
        assertTrue(tool.definition.description.contains("virtual threads are not included"))
        assertTrue(result.content.contains("\"deadlockedThreads\":0"))
        assertTrue(result.content.contains("\"name\":\"worker\""))
        assertTrue(result.content.contains("\"stack\":[\"example.Work.run(Work.kt:10)\"]"))
    }

    @Test
    fun `JVM probe reports summary and respects dump frame and thread bounds`() {
        val probe = JvmThreadDiagnosticsProbe()

        val summary = probe.inspect(ThreadDiagnosticsRequest("summary", null, 1, 1))
        val dump = probe.inspect(ThreadDiagnosticsRequest("dump", null, 1, 1))

        assertTrue(summary.summary.liveThreads > 0)
        assertEquals("summary", summary.action)
        assertTrue(summary.threads.isEmpty())
        assertTrue(dump.threads.size <= 1)
        assertTrue(dump.threads.all { it.stack.size <= 1 })
        assertTrue(
            dump.summary.states.keys
                .all { state -> Thread.State.entries.any { it.name == state } },
        )
    }
}
