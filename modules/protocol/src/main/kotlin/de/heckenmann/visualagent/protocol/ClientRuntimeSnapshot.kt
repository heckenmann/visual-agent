package de.heckenmann.visualagent.protocol

/** Bounded, non-secret runtime metrics reported by the desktop client for one conversation request. */
data class ClientRuntimeSnapshot(
    /** Client process identifier, used only to determine whether local client/server share a process. */
    val processId: Long,
    /** Operating-system name. */
    val osName: String,
    /** Operating-system version. */
    val osVersion: String,
    /** Process architecture. */
    val architecture: String,
    /** Processor count visible to the client JVM. */
    val availableProcessors: Int,
    /** Java runtime version. */
    val javaVersion: String,
    /** Java runtime vendor. */
    val jvmVendor: String,
    /** Virtual machine implementation name. */
    val vmName: String,
    /** Client JVM uptime in milliseconds. */
    val uptimeMillis: Long,
    /** Client JVM heap usage in bytes. */
    val heapUsedBytes: Long,
    /** Client JVM committed heap in bytes. */
    val heapCommittedBytes: Long,
    /** Client JVM maximum heap in bytes, when available. */
    val heapMaxBytes: Long?,
    /** Client machine physical memory in bytes, when available. */
    val totalPhysicalMemoryBytes: Long?,
    /** Client machine free physical memory in bytes, when available. */
    val freePhysicalMemoryBytes: Long?,
    /** Client process CPU load from zero to one, when available. */
    val processCpuLoad: Double?,
)

/** Captures a desktop-client JVM snapshot on the client side of the application boundary. */
fun interface ClientRuntimeDiagnosticsPort {
    /** Return a safe current-process snapshot, or null when this client cannot provide one. */
    fun snapshot(): ClientRuntimeSnapshot?
}
