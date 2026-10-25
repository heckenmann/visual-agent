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
                val liveGeneration = conversationState.liveTools.generation
                scope.launch(Dispatchers.Main) {
                    if (liveGeneration != conversationState.liveTools.generation) return@launch
                    conversationState.liveTools.accept(event, liveGeneration)
                    if (event.phase != ToolActivityPhase.UPDATED) refreshHistory()
                }
            }
        val initialGeneration = conversationState.liveTools.generation
        val liveSnapshotJob =
            scope.launch {
                val snapshots = withContext(Dispatchers.IO) { activityPort.activeToolActivities() }
                snapshots.forEach { conversationState.liveTools.accept(it, initialGeneration) }
            }
        val downloadHandle = activityPort.addDownloadListener { refreshHistory() }
        val completionHandle = suggestionPort.addCompletionListener { refreshHistory() }
        onDispose {
            liveSnapshotJob.cancel()
            toolHandle.close()
            downloadHandle.close()
            completionHandle.close()
        }
    }
}
