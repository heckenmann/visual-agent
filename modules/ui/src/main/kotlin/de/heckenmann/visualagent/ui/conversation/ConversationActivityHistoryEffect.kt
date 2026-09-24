package de.heckenmann.visualagent.ui.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.ToolActivityPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Refreshes conversation history after server-side tool and workspace download activity. */
@Composable
internal fun ConversationActivityHistoryEffect(
    activityPort: ActivityPort,
    conversationPort: ConversationPort,
    conversationState: ConversationUiState,
) {
    val scope = rememberCoroutineScope()
    DisposableEffect(activityPort) {
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
        onDispose {
            toolHandle.close()
            downloadHandle.close()
        }
    }
}
