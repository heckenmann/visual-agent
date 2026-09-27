package de.heckenmann.visualagent.agent.tools

import org.springframework.stereotype.Component
import java.lang.management.ManagementFactory
import java.util.Locale

/**
 * Snapshot of non-sensitive JVM and operating-system runtime metrics.
 *
 * @property osName Operating-system name
 * @property osVersion Operating-system version
 * @property architecture Server architecture
 * @property availableProcessors Processor count available to the JVM
 * @property javaVersion Java runtime version
 * @property jvmVendor Java runtime vendor
 * @property vmName JVM implementation name
 * @property uptimeMillis JVM process uptime in milliseconds
 * @property heapUsedBytes Current heap usage in bytes
 * @property heapCommittedBytes Committed heap memory in bytes
 * @property heapMaxBytes Maximum heap memory in bytes
 * @property nonHeapUsedBytes Current non-heap usage in bytes
 * @property totalPhysicalMemoryBytes Total physical memory in bytes, when available
 * @property freePhysicalMemoryBytes Free physical memory in bytes, when available
 * @property processCpuLoad Process CPU load from zero to one, when available
 * @property systemCpuLoad System CPU load from zero to one, when available
 */
data class RuntimeDiagnosticSnapshot(
    val osName: String,
    val osVersion: String,
    val architecture: String,
    val availableProcessors: Int,
    val javaVersion: String,
    val jvmVendor: String,
    val vmName: String,
    val uptimeMillis: Long,
    val heapUsedBytes: Long,
    val heapCommittedBytes: Long,
    val heapMaxBytes: Long,
    val nonHeapUsedBytes: Long,
    val totalPhysicalMemoryBytes: Long?,
    val freePhysicalMemoryBytes: Long?,
    val processCpuLoad: Double?,
    val systemCpuLoad: Double?,
)

/** Supplies a runtime snapshot to the context tool; replaceable in deterministic tests. */
fun interface RuntimeDiagnosticsProvider {
    /** Capture one point-in-time server runtime snapshot. */
    fun snapshot(): RuntimeDiagnosticSnapshot
}

/** Captures safe JVM and OS metrics through standard management interfaces where available. */
@Component
class JvmRuntimeDiagnosticsProvider : RuntimeDiagnosticsProvider {
    override fun snapshot(): RuntimeDiagnosticSnapshot {
        val runtime = Runtime.getRuntime()
        val osBean = ManagementFactory.getOperatingSystemMXBean()
        val memory = ManagementFactory.getMemoryMXBean()
        val runtimeBean = ManagementFactory.getRuntimeMXBean()
        val physicalMemory = osBean as? com.sun.management.OperatingSystemMXBean
        val heap = memory.heapMemoryUsage
        val nonHeap = memory.nonHeapMemoryUsage
        return RuntimeDiagnosticSnapshot(
            osName = System.getProperty("os.name"),
            osVersion = System.getProperty("os.version"),
            architecture = System.getProperty("os.arch"),
            availableProcessors = runtime.availableProcessors(),
            javaVersion = System.getProperty("java.version"),
            jvmVendor = System.getProperty("java.vendor"),
            vmName = runtimeBean.vmName,
            uptimeMillis = runtimeBean.uptime,
            heapUsedBytes = heap.used,
            heapCommittedBytes = heap.committed,
            heapMaxBytes = heap.max,
            nonHeapUsedBytes = nonHeap.used,
            totalPhysicalMemoryBytes = physicalMemory?.totalMemorySize?.takeIf { it >= 0 },
            freePhysicalMemoryBytes = physicalMemory?.freeMemorySize?.takeIf { it >= 0 },
            processCpuLoad = physicalMemory?.processCpuLoad?.validLoad(),
            systemCpuLoad = physicalMemory?.cpuLoad?.validLoad(),
        )
    }
}

/** Formats a concise runtime snapshot without exposing environment variables or system properties wholesale. */
fun RuntimeDiagnosticSnapshot.toContextText(): String =
    buildString {
        appendLine("Visual Agent server JVM runtime (a separate desktop client's JVM is not included):")
        appendLine("  OS: $osName $osVersion ($architecture)")
        appendLine("  Available processors: $availableProcessors")
        appendLine("  Java: $javaVersion; $jvmVendor; $vmName")
        appendLine("  JVM uptime: ${formatUptime(uptimeMillis)}")
        appendLine(
            "  Heap used/committed/max: ${formatBytes(heapUsedBytes)} / ${formatBytes(heapCommittedBytes)} / ${formatBytes(heapMaxBytes)}",
        )
        appendLine("  Non-heap used: ${formatBytes(nonHeapUsedBytes)}")
        totalPhysicalMemoryBytes?.let { appendLine("  Physical memory total: ${formatBytes(it)}") }
        freePhysicalMemoryBytes?.let { appendLine("  Physical memory free: ${formatBytes(it)}") }
        processCpuLoad?.let { appendLine("  Process CPU load: ${formatPercent(it)}") }
        systemCpuLoad?.let { appendLine("  System CPU load: ${formatPercent(it)}") }
        trimEnd()
    }

private fun Double.validLoad(): Double? = takeIf { isFinite() && it >= 0.0 }

private fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "unavailable"
    val units = listOf("B", "KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return "${"%.1f".format(Locale.ROOT, value)} ${units[unit]}"
}

private fun formatPercent(load: Double): String = "${"%.1f".format(Locale.ROOT, load * 100)}%"

private fun formatUptime(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSeconds = seconds % 60
    return "${hours}h ${minutes}m ${remainingSeconds}s"
}
