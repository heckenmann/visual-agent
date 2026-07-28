@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
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
 * number of newly loaded messages.
 */
@Composable
internal fun ConversationOlderHistoryLoader(
    isAtTop: Boolean,
    history: List<Message>,
    listState: LazyListState,
    agentManager: AgentManager,
    onHistoryChange: (List<Message>) -> Unit,
) {
    var loadingOlder by remember { mutableStateOf(false) }
    LaunchedEffect(isAtTop) {
        if (isAtTop && !loadingOlder && history.isNotEmpty()) {
            loadingOlder = true
            val previousFirstIndex = listState.firstVisibleItemIndex
            val previousOffset = listState.firstVisibleItemScrollOffset
            val loaded = agentManager.loadOlderHistory()
            val existingIds = history.map { it.id }.toSet()
            val newMessages = loaded.filter { it.id !in existingIds }
            if (newMessages.isNotEmpty()) {
                onHistoryChange(agentManager.getHistory())
                listState.scrollToItem(previousFirstIndex + newMessages.size, previousOffset)
            }
            loadingOlder = false
        }
    }
}

/**
 * Shows a floating scroll-to-bottom button when the user is not at the bottom of the conversation.
 */
@Composable
internal fun ConversationScrollToBottomArea(
    isAtBottom: Boolean,
    history: List<Message>,
    listState: LazyListState,
    scope: CoroutineScope,
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
                modifier = Modifier.padding(end = 12.dp, bottom = 12.dp),
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
