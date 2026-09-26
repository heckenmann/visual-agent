package de.heckenmann.visualagent.ui.application

import de.heckenmann.visualagent.protocol.CancellationToken
import de.heckenmann.visualagent.protocol.ClientRuntimeDiagnosticsPort
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.ConversationStreamRequest
import de.heckenmann.visualagent.protocol.ConversationStreamResult
import de.heckenmann.visualagent.protocol.ConversationStreamUpdate

/** Attaches client JVM data to an individual request without exposing it to ordinary conversation context. */
internal class ClientRuntimeConversationPort(
    private val delegate: ConversationPort,
    private val diagnostics: ClientRuntimeDiagnosticsPort,
) : ConversationPort by delegate {
    override suspend fun stream(
        request: ConversationStreamRequest,
        token: CancellationToken,
        onChunk: (ConversationStreamUpdate) -> Unit,
    ): ConversationStreamResult {
        val snapshot = runCatching { diagnostics.snapshot() }.getOrNull()
        return delegate.stream(request.copy(clientRuntime = snapshot), token, onChunk)
    }
}
