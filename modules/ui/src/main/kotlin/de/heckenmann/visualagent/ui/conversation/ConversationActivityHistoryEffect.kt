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
        val snapshotSync = conversationState.liveTools.beginSnapshotSync()

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
                val liveGeneration = conversationState.liveTools.generation
                // The composition-owned scope uses this host's UI dispatcher, including offscreen hosts.
                scope.launch {
                    if (liveGeneration != conversationState.liveTools.generation) return@launch
                    conversationState.liveTools.accept(event, liveGeneration)
                    if (event.phase != ToolActivityPhase.UPDATED) refreshHistory()
                }
            }
        val liveSnapshotJob =
            scope.launch {
                try {
                    val snapshots = withContext(Dispatchers.IO) { activityPort.activeToolActivities() }
                    conversationState.liveTools.completeSnapshotSync(snapshots, snapshotSync)
                } finally {
                    conversationState.liveTools.cancelSnapshotSync(snapshotSync)
                }
            }
        val downloadHandle = activityPort.addDownloadListener { refreshHistory() }
        val completionHandle = suggestionPort.addCompletionListener { refreshHistory() }
        onDispose {
            liveSnapshotJob.cancel()
            conversationState.liveTools.cancelSnapshotSync(snapshotSync)
            toolHandle.close()
            downloadHandle.close()
            completionHandle.close()
        }
    }
}
