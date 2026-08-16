package de.heckenmann.visualagent.ui.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import de.heckenmann.visualagent.protocol.ConversationPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads server-owned conversation history without blocking the Compose dispatcher. */
@Composable
internal fun loadConversationHistory(
    conversationPort: ConversationPort,
    conversationState: ConversationUiState,
) {
    LaunchedEffect(conversationPort) {
        val history = withContext(Dispatchers.IO) { conversationPort.currentHistory() }
        conversationState.replaceHistory(history)
    }
}
