package de.heckenmann.visualagent.agent.tools

/** Safe runtime metrics returned by the desktop client in response to an explicit server request. */
data class ClientRuntimeReport(
    /** Client process identifier. */
    val processId: Long,
    /** Client operating-system name. */
    val osName: String,
    /** Client operating-system version. */
    val osVersion: String,
    /** Client process architecture. */
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

/** Bounded process inventory page returned by the desktop client after an explicit server request. */
data class ClientProcessInventoryReport(
    /** Zero-based offset returned for a list request, or zero for a show request. */
    val offset: Int,
    /** Number of process identifiers visible when the client collected the page. */
    val totalProcesses: Int,
    /** Process entries in the requested page. */
    val processes: List<HostProcessEntry>,
    /** Whether more entries follow this page. */
    val hasMore: Boolean,
)
