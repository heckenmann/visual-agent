package de.heckenmann.visualagent.ui.application

import de.heckenmann.visualagent.protocol.CancellationToken
import de.heckenmann.visualagent.protocol.ClientProcessInventoryDiagnosticsPort
import de.heckenmann.visualagent.protocol.ClientProcessInventoryRequest
import de.heckenmann.visualagent.protocol.ClientRuntimeDiagnosticsPort
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.ConversationStreamRequest
import de.heckenmann.visualagent.protocol.ConversationStreamResult
import de.heckenmann.visualagent.protocol.ConversationStreamUpdate

/** Exposes request-scoped client data sources without collecting data during ordinary chats. */
internal class ClientRuntimeConversationPort(
    private val delegate: ConversationPort,
    private val diagnostics: ClientRuntimeDiagnosticsPort,
    private val processDiagnostics: ClientProcessInventoryDiagnosticsPort = ClientProcessInventoryDiagnosticsPort { null },
) : ConversationPort by delegate {
    override suspend fun stream(
        request: ConversationStreamRequest,
        token: CancellationToken,
        onChunk: (ConversationStreamUpdate) -> Unit,
    ): ConversationStreamResult {
        val requester =
            /** Provides client data only when a server-side tool explicitly requests it. */
            object : de.heckenmann.visualagent.protocol.ClientDataRequestPort {
                /** Requests the client runtime only when a server-side tool needs it. */
                override fun requestRuntimeSnapshot() = runCatching { diagnostics.snapshot() }.getOrNull()

                /** Requests client processes only when a server-side tool needs them. */
                override fun requestProcessInventory(request: ClientProcessInventoryRequest) =
                    runCatching { processDiagnostics.snapshot(request) }.getOrNull()
            }
        return delegate.stream(request.copy(clientDataRequester = requester), token, onChunk)
    }
}
