package de.heckenmann.visualagent.desktop

import de.heckenmann.visualagent.protocol.ClientRuntimeDiagnosticsPort
import de.heckenmann.visualagent.protocol.ClientRuntimeSnapshot
import java.lang.management.ManagementFactory

/** Captures safe desktop-client JVM metrics for explicit transfer with the next chat request. */
class JvmClientRuntimeDiagnosticsPort : ClientRuntimeDiagnosticsPort {
    override fun snapshot(): ClientRuntimeSnapshot {
        val os = ManagementFactory.getOperatingSystemMXBean()
        val physicalMemory = os as? com.sun.management.OperatingSystemMXBean
        val heap = ManagementFactory.getMemoryMXBean().heapMemoryUsage
        val runtime = ManagementFactory.getRuntimeMXBean()
        return ClientRuntimeSnapshot(
            processId = ProcessHandle.current().pid(),
            osName = System.getProperty("os.name").safeDescriptor(),
            osVersion = System.getProperty("os.version").safeDescriptor(),
            architecture = System.getProperty("os.arch").safeDescriptor(),
            availableProcessors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            javaVersion = System.getProperty("java.version").safeDescriptor(),
            jvmVendor = System.getProperty("java.vendor").safeDescriptor(),
            vmName = runtime.vmName.safeDescriptor(),
            uptimeMillis = runtime.uptime.coerceAtLeast(0),
            heapUsedBytes = heap.used.coerceAtLeast(0),
            heapCommittedBytes = heap.committed.coerceAtLeast(0),
            heapMaxBytes = heap.max.takeIf { it >= 0 },
            totalPhysicalMemoryBytes = physicalMemory?.totalMemorySize?.takeIf { it >= 0 },
            freePhysicalMemoryBytes = physicalMemory?.freeMemorySize?.takeIf { it >= 0 },
            processCpuLoad = physicalMemory?.processCpuLoad?.takeIf { it.isFinite() && it >= 0.0 },
        )
    }

    private fun String.safeDescriptor(): String =
        filter { it.isLetterOrDigit() || it in SAFE_DESCRIPTOR_PUNCTUATION }
            .take(MAX_DESCRIPTOR_LENGTH)
            .ifBlank { "unknown" }

    private companion object {
        const val SAFE_DESCRIPTOR_PUNCTUATION = " ._()+-/,"
        const val MAX_DESCRIPTOR_LENGTH = 96
    }
}
