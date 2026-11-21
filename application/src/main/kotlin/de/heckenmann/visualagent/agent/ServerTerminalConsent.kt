package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.tools.TerminalSandboxExecution
import de.heckenmann.visualagent.knowledge.PreferenceStore
import org.springframework.stereotype.Service

/**
 * Stores the explicit opt-in for terminal execution on this server.
 *
 * The administration boundary is responsible for authenticating the user and
 * checking their permission to change this setting.
 */
@Service
class ServerTerminalConsent(
    private val preferences: PreferenceStore,
) {
    private val lock = Any()
    private val active = mutableSetOf<TrackedExecution>()

    /** Returns true only for an explicit opt-in recorded by terminal administration. */
    fun isEnabled(): Boolean = preferences.getPreference(SERVER_TERMINAL_CONSENT_KEY) == "true"

    /** Persists consent and stops active server sandboxes when consent is withdrawn. */
    fun setEnabled(enabled: Boolean) {
        synchronized(lock) {
            preferences.setPreference(SERVER_TERMINAL_CONSENT_KEY, enabled.toString())
            if (!enabled) {
                val failures =
                    active.toList().mapNotNull { execution ->
                        runCatching { execution.cancel() }.exceptionOrNull()
                    }
                if (failures.isNotEmpty()) {
                    val failure = IllegalStateException("Could not stop all server terminal sandboxes.")
                    failures.forEach(failure::addSuppressed)
                    throw failure
                }
            }
        }
    }

    /** Starts and registers a sandbox atomically with respect to changes to consent. */
    internal fun launch(
        grantId: String?,
        start: () -> TerminalSandboxExecution?,
    ): TerminalSandboxExecution? =
        synchronized(lock) {
            if (!isEnabled()) return@synchronized null
            start()?.let { execution -> TrackedExecution(grantId, execution).also { active.add(it) } }
        }

    /** Stops commands using a grant before changing it, while holding the launch lock. */
    internal fun <T> changeGrant(
        grantId: String,
        change: () -> T,
    ): T =
        synchronized(lock) {
            active.filter { it.grantId == grantId }.forEach { it.cancel() }
            change()
        }

    private inner class TrackedExecution(
        val grantId: String?,
        private val execution: TerminalSandboxExecution,
    ) : TerminalSandboxExecution {
        override val output get() = execution.output

        override fun awaitExit(): Int = execution.awaitExit().also { synchronized(lock) { active.remove(this) } }

        override fun cancel() {
            synchronized(lock) {
                if (this in active) {
                    execution.cancel()
                    active.remove(this)
                }
            }
        }
    }
}

private const val SERVER_TERMINAL_CONSENT_KEY = "tools.terminal.server.consent.v1"
