package de.heckenmann.visualagent.agent.tools

/** Request-scoped capability for server tools to ask the desktop client for diagnostics on demand. */
interface ClientDataRequester {
    /** Requests the desktop client's current JVM report. */
    fun requestRuntimeReport(): ClientRuntimeReport?

    /** Requests one bounded page of the desktop client's process inventory. */
    fun requestProcessInventoryReport(request: ProcessInventoryRequest): ClientProcessInventoryReport?

    /** Key under which the requester is exposed only to the current provider tool invocation. */
    companion object {
        const val METADATA_KEY = "clientDataRequester"
    }
}
