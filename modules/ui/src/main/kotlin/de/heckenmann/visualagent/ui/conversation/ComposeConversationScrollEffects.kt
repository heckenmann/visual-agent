@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

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
import androidx.compose.ui.focus.FocusRequester
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
 * Fires when the newest persisted message changes, a temporary user message is displayed while a
 * request is in flight, or the streaming assistant row receives new content. The temporary message
 * is necessary because the panel renders it before it refreshes history from the database. In each
 * case, the coordinator follows only while the user remains at the newest end. Older history pages
 * do not change the newest persisted message and therefore preserve browsing.
 */
@Composable
internal fun ConversationScrollOnChangeEffect(
    history: List<Message>,
    listState: LazyListState,
    pendingUserMessage: String? = null,
    streamingContent: String = "",
    isAtLatest: Boolean = listState.conversationPosition().isAtLatest,
    onNewContentWhileBrowsing: () -> Unit = {},
) {
    var lastCount by remember { mutableStateOf(history.size) }
    var lastNewestMessage by remember { mutableStateOf(history.lastOrNull()) }
    var lastPendingUserMessage by remember { mutableStateOf(pendingUserMessage) }
    var lastStreamingContent by remember { mutableStateOf(streamingContent) }
    LaunchedEffect(history.size, pendingUserMessage, streamingContent, isAtLatest) {
        val appendedLatestHistory =
            history.isNotEmpty() && history.size > lastCount && history.lastOrNull() != lastNewestMessage
        val displayedPendingMessage = pendingUserMessage != null && pendingUserMessage != lastPendingUserMessage
        val updatedStreamingContent = streamingContent.isNotEmpty() && streamingContent != lastStreamingContent
        if (isAtLatest && (appendedLatestHistory || displayedPendingMessage || updatedStreamingContent)) {
            withFrameNanos { }
            listState.scrollToBottom()
        } else if (!isAtLatest && (appendedLatestHistory || displayedPendingMessage || updatedStreamingContent)) {
            onNewContentWhileBrowsing()
        }
        lastCount = history.size
        lastNewestMessage = history.lastOrNull()
        lastPendingUserMessage = pendingUserMessage
        lastStreamingContent = streamingContent
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
            withFrameNanos { }
            listState.scrollToBottom()
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
    inputFocusRequester: FocusRequester,
    onInputChange: (String) -> Unit,
    onSendingChange: (Boolean) -> Unit,
    onStatusChange: (String) -> Unit,
    onHistoryChange: (List<Message>) -> Unit,
    onActiveTokenChange: (de.heckenmann.visualagent.protocol.CancellationToken?) -> Unit,
    onPendingUserMessageChange: (String?) -> Unit,
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
                        }
                        QueueFlushMode.ALL_AT_ONCE -> {
                            val combined = queue.messages.joinToString("\n\n") { it.content }
                            queue.clear()
                            executeSend(
                                content = combined,
                                messageGateway = messageGateway,
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
