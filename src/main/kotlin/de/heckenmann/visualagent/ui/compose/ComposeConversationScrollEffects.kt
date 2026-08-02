@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Scrolls a [LazyListState] to the bottom of its content.
 *
 * When the LazyColumn uses [androidx.compose.foundation.lazy.LazyListScope.reverseLayout] = true,
 * the newest items are at index 0, so scrolling to the bottom is simply
 * [LazyListState.scrollToItem]`(0)`. No retry loops or [Int.MAX_VALUE] hacks
 * are needed — the layout engine handles this correctly on the next frame.
 */
internal suspend fun LazyListState.scrollToBottom() {
    if (layoutInfo.totalItemsCount == 0) return
    scrollToItem(0)
}

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
            // Retry until items are laid out (handles async Markdown rendering
            // where items may initially report height 0).
            var attempts = 0
            while (listState.layoutInfo.totalItemsCount == 0 && attempts < 10) {
                kotlinx.coroutines.delay(16)
                attempts++
            }
            listState.scrollToBottom()
        }
    }
}

/**
 * Keeps the conversation list scrolled to the bottom when a new message is displayed.
 *
 * Fires when persisted history grows or a temporary user message is displayed while
 * a request is in flight. The latter is necessary because the panel renders the
 * pending message before it refreshes history from the database. In both cases, the
 * scroll waits for the next frame so the LazyColumn has measured the new item.
 * Refreshes that only replace existing history retain the user's scroll position.
 */
@Composable
internal fun ConversationScrollOnChangeEffect(
    history: List<Message>,
    listState: LazyListState,
    pendingUserMessage: String? = null,
) {
    var lastCount by remember { mutableStateOf(history.size) }
    var lastPendingUserMessage by remember { mutableStateOf(pendingUserMessage) }
    LaunchedEffect(history.size, pendingUserMessage) {
        val appendedHistory = history.isNotEmpty() && history.size > lastCount
        val displayedPendingMessage = pendingUserMessage != null && pendingUserMessage != lastPendingUserMessage
        if (appendedHistory || displayedPendingMessage) {
            withFrameNanos { }
            listState.scrollToBottom()
        }
        lastCount = history.size
        lastPendingUserMessage = pendingUserMessage
    }
}

/**
 * Scrolls to the bottom of the conversation list when the panel is resized.
 *
 * A short debounce prevents scrolling during intermediate resize frames.
 */
@Composable
internal fun ConversationResizeScrollEffect(
    panelSize: IntSize,
    history: List<Message>,
    listState: LazyListState,
) {
    LaunchedEffect(panelSize) {
        if (panelSize != IntSize.Zero) {
            delay(200)
            if (history.isNotEmpty()) {
                listState.scrollToBottom()
            }
        }
    }
}

/**
 * Automatically loads older history when the user scrolls to the end of the conversation.
 *
 * With [reverseLayout] = true, the oldest messages are at the end (highest indices),
 * so "scrolling to the top" in the visual sense means scrolling to the last item.
 * Preserves scroll position by adjusting [LazyListState.firstVisibleItemIndex] by the
 * number of newly loaded messages. Tracks whether more history is available so it
 * does not re-fire once all pages are exhausted.
 *
 * @param isAtEnd true when the user has scrolled to the last visible item (oldest messages).
 * @param history current in-memory conversation history.
 * @param listState the [LazyListState] of the conversation [LazyColumn].
 * @param agentManager the agent manager used to load older pages.
 * @param onHistoryChange called with the updated history list after a successful load.
 * @param onLoadStateChange called with `true` when loading starts and `false` when it
 *   finishes; the panel uses this to show a loading indicator and to disable the manual
 *   button.
 * @param onHasMoreHistoryChange called with `false` when a load returns no new messages,
 *   signalling that no older pages remain.
 */
@Composable
internal fun ConversationOlderHistoryLoader(
    isAtEnd: Boolean,
    history: List<Message>,
    listState: LazyListState,
    agentManager: AgentManager,
    onHistoryChange: (List<Message>) -> Unit,
    onLoadStateChange: (Boolean) -> Unit = {},
    onHasMoreHistoryChange: (Boolean) -> Unit = {},
) {
    var loadingOlder by remember { mutableStateOf(false) }
    var hasMoreHistory by remember { mutableStateOf(true) }
    LaunchedEffect(isAtEnd) {
        if (isAtEnd && !loadingOlder && hasMoreHistory && history.isNotEmpty()) {
            loadingOlder = true
            onLoadStateChange(true)
            val previousFirstIndex = listState.firstVisibleItemIndex
            val previousOffset = listState.firstVisibleItemScrollOffset
            val loaded = agentManager.loadOlderHistory()
            val existingIds = history.map { it.id }.toSet()
            val newMessages = loaded.filter { it.id !in existingIds }
            if (newMessages.isNotEmpty()) {
                onHistoryChange(agentManager.getHistory())
                // With reverseLayout, older messages are appended at the end.
                // Preserve the user's scroll position by adjusting the index.
                listState.scrollToItem(previousFirstIndex + newMessages.size, previousOffset)
            } else {
                hasMoreHistory = false
                onHasMoreHistoryChange(false)
            }
            loadingOlder = false
            onLoadStateChange(false)
        }
    }
}

/**
 * Shows a floating scroll-to-bottom button when the user is not at the bottom of the conversation.
 *
 * On click, optionally refreshes the in-memory history from the database (picking
 * up any messages written by background processes) and then scrolls to the
 * newest persisted message.
 *
 * @param agentManager used to reload the latest history from the database before
 *   scrolling. If `null`, no DB refresh is performed (useful for isolated tests).
 * @param onHistoryRefresh called after the DB refresh so the caller can update
 *   its local `history` state (which drives the LazyColumn items). No-op if
 *   [agentManager] is `null`.
 * @param bottomPadding extra bottom padding (in pixels) to keep the button above an overlapping
 *   bottom-anchored element (e.g. the input area).
 * @param modifier additional modifier; callers should align this to the bottom-end of the Box.
 */
@Composable
internal fun ConversationScrollToBottomArea(
    isAtBottom: Boolean,
    listState: LazyListState,
    scope: CoroutineScope,
    agentManager: AgentManager? = null,
    onHistoryRefresh: () -> Unit = {},
    bottomPadding: Int = 0,
    modifier: Modifier = Modifier,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val bottomDp = with(density) { bottomPadding.toDp() } + 12.dp
    // Wrap in a Box with fillMaxSize so the alignment is stable even when
    // AnimatedVisibility collapses to zero size during exit animation.
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.BottomEnd,
    ) {
        AnimatedVisibility(
            visible = !isAtBottom,
            enter = fadeIn(animationSpec = tween(180)) + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(targetOffsetY = { it / 2 }),
        ) {
            ScrollToBottomButton(
                onClick = {
                    scope.launch {
                        // Clear the in-memory history and reload the latest page
                        // from the DB so the user always lands on the newest
                        // persisted message, even after many background writes.
                        agentManager?.refreshHistoryToLatest()
                        onHistoryRefresh()
                        // With reverseLayout, newest items are at index 0.
                        // No delay needed — scrollToItem(0) works immediately.
                        listState.scrollToBottom()
                    }
                },
                modifier = Modifier.padding(end = 12.dp, bottom = bottomDp),
            )
        }
    }
}

/**
 * Flushes the message queue when the agent is idle, respecting the configured flush mode.
 */
@Composable
internal fun ConversationQueueFlushEffect(
    sending: Boolean,
    inFlight: InFlightStateHolder,
    queue: MessageQueue,
    agentManager: AgentManager,
    inputFocusRequester: FocusRequester,
    onInputChange: (String) -> Unit,
    onSendingChange: (Boolean) -> Unit,
    onStatusChange: (String) -> Unit,
    onHistoryChange: (List<Message>) -> Unit,
    onActiveTokenChange: (de.heckenmann.visualagent.agent.CancellationToken?) -> Unit,
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
                        }
                        QueueFlushMode.ALL_AT_ONCE -> {
                            val combined = queue.messages.joinToString("\n\n") { it.content }
                            queue.clear()
                            executeSend(
                                content = combined,
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
