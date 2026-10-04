package de.heckenmann.visualagent.ui.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.ConversationSuggestionPort
import de.heckenmann.visualagent.protocol.ToolActivityPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Refreshes history after persisted assistant completions, tool activity and workspace downloads. */
@Composable
internal fun ConversationActivityHistoryEffect(
    activityPort: ActivityPort,
    conversationPort: ConversationPort,
    conversationState: ConversationUiState,
    suggestionPort: ConversationSuggestionPort,
) {
    val scope = rememberCoroutineScope()
    DisposableEffect(activityPort, conversationPort, conversationState, suggestionPort) {
        /** Reloads the newest complete page while preserving older pages and live stream deltas. */
        fun refreshHistory() {
            scope.launch {
                val request = conversationState.beginLatestRequest()
                val page = withContext(Dispatchers.IO) { conversationPort.latest() }
                conversationState.applyLatest(request, page)
            }
        }
        val toolHandle =
            activityPort.addToolListener { event ->
                when (event.phase) {
                    ToolActivityPhase.STARTED, ToolActivityPhase.FINISHED -> refreshHistory()
                }
            }
        val downloadHandle = activityPort.addDownloadListener { refreshHistory() }
        val completionHandle = suggestionPort.addCompletionListener { refreshHistory() }
        onDispose {
            toolHandle.close()
            downloadHandle.close()
            completionHandle.close()
        }
    }
}
