package de.heckenmann.visualagent.agent.tools.api

/** Read-only connector subsystem availability summary. */
data class ServerConnectorDiagnostic(
    /** Whether an operational connector registry is available. */
    val available: Boolean,
    /** Number of configured connector instances when the registry is available. */
    val configuredCount: Int?,
    /** Stable reason category when connector diagnostics are unavailable. */
    val reason: String?,
)

/** Supplies a safe connector health summary without exposing credentials. */
fun interface ServerConnectorDiagnosticsPort {
    /** Returns the current connector registry status. */
    fun snapshot(): ServerConnectorDiagnostic
}
