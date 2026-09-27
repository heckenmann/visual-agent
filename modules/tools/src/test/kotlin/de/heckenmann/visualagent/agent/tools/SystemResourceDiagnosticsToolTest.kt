package de.heckenmann.visualagent.agent.tools

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemResourceDiagnosticsToolTest {
    @Test
    fun `system gc returns collectors and bounded pool snapshot`() {
        val tool =
            SystemGcTool(
                GcDiagnosticsProbe {
                    GcDiagnosticsSnapshot(
                        collectors = listOf(GarbageCollectorMetric("Test GC", 12, 340)),
                        memoryPools = listOf(MemoryPoolMetric("Old Gen", "HEAP", 1024, 2048, 4096)),
                        truncated = false,
                    )
                },
            )

        val result = tool.execute("{}", emptyMap())

        assertTrue(result.success)
        assertTrue(result.content.contains("\"runtimeScope\":\"visual-agent-server-jvm\""))
        assertTrue(result.content.contains("\"collectionCount\":12"))
        assertTrue(result.content.contains("\"usedBytes\":1024"))
        assertFalse(result.content.contains("heapDump"))
    }

    @Test
    fun `system process reports only its JVM process metrics`() {
        val tool =
            SystemProcessTool(
                ServerProcessMetricsProbe {
                    ServerProcessMetrics(
                        processId = 42,
                        startedAtEpochMillis = 1_700_000_000_000,
                        cpuTimeMillis = 900,
                        cpuLoad = 0.25,
                        jvmUptimeMillis = 10_000,
                        heapUsedBytes = 2048,
                        heapCommittedBytes = 4096,
                        heapMaxBytes = 8192,
                    )
                },
            )

        val result = tool.execute("{}", emptyMap())

        assertTrue(result.success)
        assertTrue(result.content.contains("\"runtimeScope\":\"visual-agent-server-process\""))
        assertTrue(result.content.contains("\"processId\":42"))
        assertTrue(result.content.contains("\"cpuTimeMillis\":900"))
        assertFalse(result.content.contains("commandLine"))
        assertFalse(result.content.contains("environment"))
    }

    @Test
    fun `JVM probes return current runtime snapshots`() {
        val gc = JvmGcDiagnosticsProbe().snapshot()
        val process = JvmServerProcessMetricsProbe().snapshot()

        assertTrue(gc.collectors.size <= 16)
        assertTrue(gc.memoryPools.size <= 32)
        assertTrue(process.processId > 0)
        assertTrue(process.jvmUptimeMillis > 0)
        assertTrue(process.heapUsedBytes >= 0)
    }
}
