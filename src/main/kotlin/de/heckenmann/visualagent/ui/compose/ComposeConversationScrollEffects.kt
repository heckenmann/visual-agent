@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
 * Scrolls the conversation list to the bottom once on composition when history is not empty.
 */
@Composable
internal fun ConversationStartupScrollEffect(
    history: List<Message>,
    listState: LazyListState,
) {
    LaunchedEffect(Unit) {
        if (history.isNotEmpty()) {
            listState.scrollToItem(history.lastIndex)
        }
    }
}

/**
 * Keeps the conversation list scrolled to the bottom when the last message changes.
 *
 * Uses [LazyListState.requestScrollToItem] (non-suspending) so the scroll is
 * posted as a request and processed in the next layout pass without blocking
 * the composition or the chunk callback.
 */
@Composable
internal fun ConversationScrollOnChangeEffect(
    history: List<Message>,
    listState: LazyListState,
) {
    val lastMessageKey = history.lastOrNull()?.let { "${it.id}:${it.content.hashCode()}" }
    LaunchedEffect(lastMessageKey) {
        if (history.isNotEmpty()) {
            listState.requestScrollToItem(history.lastIndex)
        }
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
                listState.requestScrollToItem(history.lastIndex)
            }
        }
    }
}

/**
 * Automatically loads older history when the user scrolls to the top of the conversation.
 *
 * Preserves scroll position by adjusting [LazyListState.firstVisibleItemIndex] by the
 * number of newly loaded messages. Tracks whether more history is available so it
 * does not re-fire once all pages are exhausted.
 *
 * @param isAtTop true when the user has scrolled to the first visible item.
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
    isAtTop: Boolean,
    history: List<Message>,
    listState: LazyListState,
    agentManager: AgentManager,
    onHistoryChange: (List<Message>) -> Unit,
    onLoadStateChange: (Boolean) -> Unit = {},
    onHasMoreHistoryChange: (Boolean) -> Unit = {},
) {
    var loadingOlder by remember { mutableStateOf(false) }
    var hasMoreHistory by remember { mutableStateOf(true) }
    LaunchedEffect(isAtTop) {
        if (isAtTop && !loadingOlder && hasMoreHistory && history.isNotEmpty()) {
            loadingOlder = true
            onLoadStateChange(true)
            val previousFirstIndex = listState.firstVisibleItemIndex
            val previousOffset = listState.firstVisibleItemScrollOffset
            val loaded = agentManager.loadOlderHistory()
            val existingIds = history.map { it.id }.toSet()
            val newMessages = loaded.filter { it.id !in existingIds }
            if (newMessages.isNotEmpty()) {
                onHistoryChange(agentManager.getHistory())
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
 * @param bottomPadding extra bottom padding to keep the button above an overlapping
 *   bottom-anchored element (e.g. the input area).
 */
@Composable
internal fun ConversationScrollToBottomArea(
    isAtBottom: Boolean,
    history: List<Message>,
    listState: LazyListState,
    scope: CoroutineScope,
    bottomPadding: Int = 0,
) {
    AnimatedVisibility(
        visible = !isAtBottom,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(animationSpec = tween(180)) + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(targetOffsetY = { it / 2 }),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End,
        ) {
            ScrollToBottomButton(
                onClick = { scope.launch { listState.animateScrollToItem(history.lastIndex.coerceAtLeast(0)) } },
                modifier =
                    Modifier.padding(
                        end = 12.dp,
                        bottom =
                            with(androidx.compose.ui.platform.LocalDensity.current) {
                                bottomPadding.toDp() + 12.dp
                            },
                    ),
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

/**
 * A vertical scrollbar attached to the conversation [LazyListState].
 *
 * @param listState the [LazyListState] of the conversation [LazyColumn]
 * @param modifier additional modifier; callers should align this to the right edge
 */
@Composable
internal fun ConversationVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(listState),
        modifier = modifier.padding(end = 2.dp),
    )
}
