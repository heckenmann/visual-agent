package de.heckenmann.visualagent.agent.tools

import org.springframework.stereotype.Component
import java.lang.management.ManagementFactory
import java.time.Instant

/** Reads safe metrics for the current server process using JDK process and management APIs. */
@Component
class JvmServerProcessMetricsProbe : ServerProcessMetricsProbe {
    override fun snapshot(): ServerProcessMetrics {
        val process = ProcessHandle.current()
        val processInfo = process.info()
        val runtime = ManagementFactory.getRuntimeMXBean()
        val heap = ManagementFactory.getMemoryMXBean().heapMemoryUsage
        val osBean = ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean
        val runtimeStart = runCatching { Instant.ofEpochMilli(runtime.startTime) }.getOrNull()
        val start = processInfo.startInstant().orElse(runtimeStart)
        val cpuTime = processInfo.totalCpuDuration().orElse(null)
        return ServerProcessMetrics(
            processId = process.pid(),
            startedAtEpochMillis = start?.toEpochMilli(),
            cpuTimeMillis = cpuTime?.toMillis(),
            cpuLoad = osBean?.processCpuLoad?.takeIf { it.isFinite() && it >= 0.0 },
            jvmUptimeMillis = runtime.uptime,
            heapUsedBytes = heap.used,
            heapCommittedBytes = heap.committed,
            heapMaxBytes = heap.max.takeIf { it >= 0 },
        )
    }
}
