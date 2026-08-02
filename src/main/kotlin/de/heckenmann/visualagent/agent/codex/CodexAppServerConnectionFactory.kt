package de.heckenmann.visualagent.agent.codex

import io.github.vupoint.cokit.client.ClientInfo
import io.github.vupoint.cokit.client.CodexClient
import io.github.vupoint.cokit.client.CodexClientConnection
import io.github.vupoint.cokit.client.CodexClients
import io.github.vupoint.cokit.client.InitializeCapabilities
import kotlinx.coroutines.CoroutineScope
import org.springframework.stereotype.Component
import java.nio.file.Path

/** Opens CoKit clients over sanitized Visual Agent-owned app-server processes. */
@Component
internal class CodexAppServerConnectionFactory(
    private val processFactory: CodexCliProcessFactory,
    private val applicationScope: CoroutineScope,
) : CodexAppServerConnector {
    /** Starts and initializes one CoKit app-server connection. */
    override suspend fun connect(
        executable: Path,
        workingDirectory: Path,
    ): CodexAppServerConnection {
        val child = processFactory.startAppServer(executable, workingDirectory)
        val transport = CodexCliCoKitTransport(child, applicationScope)
        return try {
            CodexAppServerConnection(
                CodexClients.connect(
                    CodexClientConnection(
                        transport = transport,
                        clientInfo = ClientInfo("visual_agent", "Visual Agent", APPLICATION_VERSION),
                        scope = applicationScope,
                        capabilities = InitializeCapabilities(experimentalApi = true),
                    ),
                ),
                transport,
            )
        } catch (error: Throwable) {
            transport.close()
            throw error
        }
    }

    private companion object {
        private const val APPLICATION_VERSION = "0.1.0"
    }
}

/** Opens initialized CoKit app-server connections. */
internal fun interface CodexAppServerConnector {
    /** Opens one connection for the selected executable and working directory. */
    suspend fun connect(
        executable: Path,
        workingDirectory: Path,
    ): CodexAppServerConnection
}

/** Initialized CoKit client and its owned sanitized process transport. */
internal class CodexAppServerConnection(
    val client: CodexClient,
    private val transport: AutoCloseable,
) : AutoCloseable {
    override fun close() {
        client.close()
        transport.close()
    }
}
