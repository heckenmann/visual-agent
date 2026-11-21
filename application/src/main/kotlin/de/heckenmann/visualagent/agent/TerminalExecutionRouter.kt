package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.tools.TerminalHost
import de.heckenmann.visualagent.agent.tools.TerminalSandboxExecution
import de.heckenmann.visualagent.agent.tools.TerminalSandboxLauncher
import de.heckenmann.visualagent.agent.tools.TerminalSandboxRequest
import de.heckenmann.visualagent.workspace.ServerTerminalDirectories
import de.heckenmann.visualagent.workspace.ServerTerminalDirectory
import org.springframework.stereotype.Component

/** Executes authorized terminal requests on the application server. */
internal fun interface ServerTerminalBackend {
    /** Starts the sandbox with the authorized directory and any narrower task scope. */
    fun start(
        request: TerminalSandboxRequest,
        directory: ServerTerminalDirectory,
        context: Map<String, Any>,
    ): TerminalSandboxExecution?
}

/** Routes server terminal invocations through the server's consent gate. */
@Component
internal class TerminalExecutionRouter(
    private val serverConsent: ServerTerminalConsent,
    private val directories: ServerTerminalDirectories,
    private val serverBackend: ServerTerminalBackend? = null,
) : TerminalSandboxLauncher {
    override fun launch(
        request: TerminalSandboxRequest,
        context: Map<String, Any>,
    ): TerminalSandboxExecution? =
        when (request.target) {
            TerminalHost.SERVER ->
                serverConsent.launch(
                    request.grantId,
                ) { serverBackend?.start(request, directories.resolve(request), context) }
            TerminalHost.CLIENT -> null
        }
}
