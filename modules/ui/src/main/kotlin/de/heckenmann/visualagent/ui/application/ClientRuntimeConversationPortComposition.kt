package de.heckenmann.visualagent.ui.application

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import de.heckenmann.visualagent.protocol.ConversationPort

/** Remembers the client-aware conversation boundary for the supplied desktop dependencies. */
@Composable
internal fun rememberClientRuntimeConversationPort(deps: ComposeApplicationDependencies): ConversationPort =
    remember(deps.applicationPort.conversation, deps.clientRuntimeDiagnostics) {
        ClientRuntimeConversationPort(deps.applicationPort.conversation, deps.clientRuntimeDiagnostics)
    }
