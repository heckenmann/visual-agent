@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.CancellationToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Renders the conversation panel overlays that are independent from the message list. */
@Composable
internal fun ConversationPanelOverlays(
    isAtBottom: Boolean,
    listState: LazyListState,
    scope: CoroutineScope,
    agentManager: AgentManager,
    inputAreaHeight: Int,
    editingId: String?,
    history: List<de.heckenmann.visualagent.agent.Message>,
    onHistoryRefresh: () -> Unit,
    onDismissEdit: () -> Unit,
) {
    ConversationScrollToBottomArea(
        isAtBottom = isAtBottom,
        listState = listState,
        scope = scope,
        agentManager = agentManager,
        onHistoryRefresh = onHistoryRefresh,
        bottomPadding = inputAreaHeight,
    )
    ConversationEditModal(
        editingId = editingId,
        history = history,
        agentManager = agentManager,
        onDismiss = onDismissEdit,
        onHistoryRefresh = onHistoryRefresh,
    )
}

/** Renders the queued-message controls and starts queued requests on demand. */
@Composable
internal fun ConversationPanelQueueStrip(
    queue: MessageQueue,
    scope: CoroutineScope,
    inFlight: InFlightStateHolder,
    agentManager: AgentManager,
    inputFocusRequester: androidx.compose.ui.focus.FocusRequester,
    activeToken: () -> CancellationToken?,
    onInputChange: (String) -> Unit,
    onSendingChange: (Boolean) -> Unit,
    onStatusChange: (String) -> Unit,
    onHistoryChange: (List<de.heckenmann.visualagent.agent.Message>) -> Unit,
    onActiveTokenChange: (CancellationToken?) -> Unit,
    onPendingUserMessageChange: (String?) -> Unit,
    streamingFlow: MutableStateFlow<String>,
) {
    MessageQueueStrip(
        queue = queue,
        onSendNow = { msg ->
            queue.remove(msg.id)
            activeToken()?.cancel()
            scope.launch {
                delay(200)
                executeSend(
                    content = msg.content,
                    agentManager = agentManager,
                    inFlight = inFlight,
                    inputFocusRequester = inputFocusRequester,
                    onInputChange = onInputChange,
                    onSendingChange = onSendingChange,
                    onStatusChange = onStatusChange,
                    onHistoryChange = onHistoryChange,
                    onActiveTokenChange = onActiveTokenChange,
                    onPendingUserMessageChange = onPendingUserMessageChange,
                    streamingFlow = streamingFlow,
                )
            }
        },
        onClear = { queue.clear() },
        onToggleFlushMode = {
            queue.flushMode =
                if (queue.flushMode == QueueFlushMode.ONE_BY_ONE) {
                    QueueFlushMode.ALL_AT_ONCE
                } else {
                    QueueFlushMode.ONE_BY_ONE
                }
        },
    )
}
