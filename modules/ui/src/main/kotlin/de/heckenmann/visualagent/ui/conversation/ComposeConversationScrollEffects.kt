package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.ui.agents.*
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.canvas.*
import de.heckenmann.visualagent.ui.components.*
import de.heckenmann.visualagent.ui.conversation.*
import de.heckenmann.visualagent.ui.files.*
import de.heckenmann.visualagent.ui.modal.*
import de.heckenmann.visualagent.ui.settings.*
import de.heckenmann.visualagent.ui.status.*
import de.heckenmann.visualagent.ui.todo.*
import de.heckenmann.visualagent.ui.workspace.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import de.heckenmann.visualagent.protocol.ConversationMessage as Message

/**
 * Scrolls the conversation list to the bottom once on composition when history is not empty.
 */
@Composable
internal fun ConversationStartupScrollEffect(
    history: List<Message>,
    listState: LazyListState,
) {
    LaunchedEffect(Unit) {
        if (history.isNotEmpty()) {
            withFrameNanos { }
            listState.scrollToBottom()
        }
    }
}

/**
 * Keeps the conversation list scrolled to the bottom when a new message is displayed.
 *
 * Observes the rendered timeline uniformly, including todo cards, grouped tool results, pending
 * messages, and streaming output. New activity always moves to the newest end. Positioning is requested
 * for the next measure without suspending, so another chunk or position change cannot cancel
 * observation bookkeeping. Older history pages preserve browsing.
 */
@Composable
internal fun ConversationScrollOnChangeEffect(
    timeline: List<ConversationTimelineItem>,
    listState: LazyListState,
) {
    val timelineSnapshot = conversationTimelineScrollSnapshot(timeline)
    var lastTimelineSnapshot by remember(listState) { mutableStateOf(timelineSnapshot) }
    LaunchedEffect(listState, timelineSnapshot) {
        if (timelineSnapshot.hasNewContentSince(lastTimelineSnapshot)) {
            listState.requestScrollToItem(0)
        }
        lastTimelineSnapshot = timelineSnapshot
    }
}

/**
 * Keeps the conversation at its newest end when the visible viewport changes.
 *
 * The viewport size is structural: a fixed composer reserves layout space outside the
 * timeline. Browsed history and user movement are preserved.
 */
@Composable
internal fun ConversationResizeScrollEffect(
    viewportSize: IntSize,
    hasConversationContent: Boolean,
    listState: LazyListState,
    isAtLatest: Boolean = listState.conversationPosition().isAtLatest,
) {
    LaunchedEffect(viewportSize) {
        if (isAtLatest && viewportSize != IntSize.Zero && hasConversationContent) {
            listState.requestScrollToItem(0)
        }
    }
}

/**
 * Determines whether the older-history loading indicator should be displayed.
 *
 * @param isLoadingOlder Whether an older-history request is currently active
 * @param hasMoreHistory Whether older history pages remain available
 * @return `true` only while a request is active and more history is available
 */
internal fun shouldShowOlderHistoryLoadingIndicator(
    isLoadingOlder: Boolean,
    hasMoreHistory: Boolean,
): Boolean = isLoadingOlder && hasMoreHistory

/**
 * Flushes the message queue when the agent is idle, respecting the configured flush mode.
 */
@Composable
internal fun ConversationQueueFlushEffect(
    sending: Boolean,
    inFlight: InFlightStateHolder,
    queue: MessageQueue,
    messageGateway: ConversationMessageGateway,
    onInputChange: (String) -> Unit,
    onSendingChange: (Boolean) -> Unit,
    onStatusChange: (String) -> Unit,
    onActiveTokenChange: (de.heckenmann.visualagent.protocol.CancellationToken?) -> Unit,
    onPendingUserMessageChange: (String?) -> Unit,
    onPendingUserEntryIdChange: (String?) -> Unit,
    onStreamingEntryIdChange: (String?) -> Unit,
    onStreamCompletion: (List<de.heckenmann.visualagent.protocol.ConversationMessage>) -> Unit,
    streamingFlow: MutableStateFlow<String>,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(sending, inFlight.state.value.totalActive, queue.messages.size) {
        if (!sending && inFlight.state.value.totalActive == 0 && queue.isNotEmpty && !queue.flushing) {
            queue.flushing = true
            scope.launch {
                try {
                    when (queue.flushMode) {
                        QueueFlushMode.ONE_BY_ONE -> {
                            while (queue.isNotEmpty) {
                                val msg = queue.dequeue() ?: break
                                executeSend(
                                    content = msg.content,
                                    messageGateway = messageGateway,
                                    inFlight = inFlight,
                                    onInputChange = onInputChange,
                                    onSendingChange = onSendingChange,
                                    onStatusChange = onStatusChange,
                                    onActiveTokenChange = onActiveTokenChange,
                                    onPendingUserMessageChange = onPendingUserMessageChange,
                                    onPendingUserEntryIdChange = onPendingUserEntryIdChange,
                                    onStreamingEntryIdChange = onStreamingEntryIdChange,
                                    onStreamCompletion = onStreamCompletion,
                                    streamingFlow = streamingFlow,
                                )
                            }
                        }
                        QueueFlushMode.ALL_AT_ONCE -> {
                            val combined = queue.messages.joinToString("\n\n") { it.content }
                            queue.clear()
                            executeSend(
                                content = combined,
                                messageGateway = messageGateway,
                                inFlight = inFlight,
                                onInputChange = onInputChange,
                                onSendingChange = onSendingChange,
                                onStatusChange = onStatusChange,
                                onActiveTokenChange = onActiveTokenChange,
                                onPendingUserMessageChange = onPendingUserMessageChange,
                                onPendingUserEntryIdChange = onPendingUserEntryIdChange,
                                onStreamingEntryIdChange = onStreamingEntryIdChange,
                                onStreamCompletion = onStreamCompletion,
                                streamingFlow = streamingFlow,
                            )
                        }
                    }
                } finally {
                    queue.flushing = false
                }
            }
        }
    }
}

/**
 * Small centered [CircularProgressIndicator] shown at the top of the conversation
 * list while older history is being fetched.
 */
@Composable
internal fun OlderHistoryLoadingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
    }
}
