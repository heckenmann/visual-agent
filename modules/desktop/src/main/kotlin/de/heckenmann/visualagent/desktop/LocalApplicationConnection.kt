package de.heckenmann.visualagent.desktop

import de.heckenmann.visualagent.protocol.ApplicationConnection
import de.heckenmann.visualagent.protocol.ApplicationPort
import de.heckenmann.visualagent.server.VisualAgentGrpcServer

/** In-process application connection used by the default single-JVM desktop deployment. */
internal class LocalApplicationConnection(
    private val server: VisualAgentGrpcServer,
    private val applicationProvider: () -> ApplicationPort,
) : ApplicationConnection {
    private var ready = false

    override val application: ApplicationPort
        get() {
            check(ready) { "The application connection is not ready" }
            return applicationProvider()
        }

    override suspend fun awaitReady() {
        check(server.isReady()) { "The local application server is not ready" }
        awaitProtocolHandshake(DesktopServerEndpoint.LocalInProcess(server.inProcessServerName()))
        ready = true
    }

    override fun close() {
        ready = false
    }
}
