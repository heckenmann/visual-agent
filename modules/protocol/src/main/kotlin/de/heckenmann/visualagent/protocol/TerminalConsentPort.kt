package de.heckenmann.visualagent.protocol

/** Direct-user administration for one terminal execution target. */
interface TerminalConsentPort {
    /** Returns the current consent and launch readiness for the target. */
    fun snapshot(): TerminalConsentSnapshot

    /** Records the user's confirmed consent or revokes it and stops active invocations. */
    fun setEnabled(enabled: Boolean): TerminalConsentSnapshot
}

/** State displayed by the terminal consent control for a single host. */
data class TerminalConsentSnapshot(
    /** Whether the user has explicitly enabled this host. */
    val enabled: Boolean,
    /** Whether the host can currently accept new consent. */
    val canEnable: Boolean,
    /** Which host and OS account are used for execution. */
    val executionNotice: String,
    /** Current directory labels and access modes. */
    val directories: List<String>,
)
