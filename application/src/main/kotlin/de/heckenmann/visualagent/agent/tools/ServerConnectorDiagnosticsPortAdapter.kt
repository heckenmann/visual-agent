package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ServerConnectorDiagnostic
import de.heckenmann.visualagent.agent.tools.api.ServerConnectorDiagnosticsPort
import org.springframework.stereotype.Component

/** Reports unavailable until the modular connector registry from issue #52 is implemented. */
@Component
class ServerConnectorDiagnosticsPortAdapter : ServerConnectorDiagnosticsPort {
    override fun snapshot() =
        ServerConnectorDiagnostic(available = false, configuredCount = null, reason = "connector_framework_not_configured")
}
