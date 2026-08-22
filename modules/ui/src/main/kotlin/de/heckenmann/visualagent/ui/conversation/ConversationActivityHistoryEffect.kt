@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

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
        /** Reloads persisted conversation messages when the UI is idle. */
        fun refreshHistory() {
            scope.launch {
                if (!conversationState.sending) {
                    val history = withContext(Dispatchers.IO) { conversationPort.currentHistory() }
                    conversationState.replaceHistory(history)
                }
            }
        }
        val toolHandle =
            activityPort.addToolListener { event ->
                if (event.phase == ToolActivityPhase.FINISHED) refreshHistory()
            }
        val downloadHandle = activityPort.addDownloadListener { refreshHistory() }
        onDispose {
            toolHandle.close()
            downloadHandle.close()
        }
    }
}
