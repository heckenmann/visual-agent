@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.todo.TodoEventBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Conversation panel with message history, streaming input, message queue, and todo actions.
 *
 * Use cases: UC-0000002, UC-0000003, UC-0000004, UC-0000045, UC-0000046, UC-0000049, UC-0000071.
 */
@Composable
internal fun ConversationPanel(
    agentManager: AgentManager,
    modalRequester: ComposeModalRequester,
    inFlight: InFlightStateHolder,
    toolEventBus: ToolEventBus,
    todoEventBus: TodoEventBus,
    config: AppConfigBean,
) {
    val scope = rememberCoroutineScope()
    val inputFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var history by remember { mutableStateOf(agentManager.getHistory()) }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready") }
    var sending by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var deletingMessageIds by remember { mutableStateOf(setOf<String>()) }
    var activeToken by remember { mutableStateOf<CancellationToken?>(null) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    val queue = remember { MessageQueue() }
    LaunchedEffect(config.queueFlushMode) {
        queue.flushMode =
            try {
                QueueFlushMode.valueOf(config.queueFlushMode)
            } catch (_: IllegalArgumentException) {
                QueueFlushMode.ONE_BY_ONE
            }
    }
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= info.totalItemsCount - 2
        }
    }
    val isAtTop by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()
            first != null && first.index == 0
        }
    }
    ConversationOlderHistoryLoader(
        isAtTop = isAtTop,
        history = history,
        listState = listState,
        agentManager = agentManager,
        onHistoryChange = { history = it },
    )
    DisposableEffect(toolEventBus) {
        val handle =
            toolEventBus.addListener { event ->
                if (event.phase == ToolCallPhase.FINISHED) {
                    history = agentManager.getHistory()
                }
            }
        onDispose { handle.close() }
    }
    DisposableEffect(todoEventBus) {
        val handle =
            todoEventBus.addListener {
                history = agentManager.getHistory()
            }
        onDispose { handle.close() }
    }
    val sendContent: (String) -> Unit = { rawContent ->
        val content = rawContent.trim()
        if (content.isNotBlank()) {
            if (sending) {
                queue.enqueue(content, QueuedMessageSource.USER)
                status = "Queued (${queue.size})"
            } else {
                scope.launch {
                    executeSend(
                        content = content,
                        agentManager = agentManager,
                        inFlight = inFlight,
                        inputFocusRequester = inputFocusRequester,
                        onInputChange = { input = it },
                        onSendingChange = { sending = it },
                        onStatusChange = { status = it },
                        onHistoryChange = { history = it },
                        onActiveTokenChange = { activeToken = it },
                    )
                }
            }
        }
    }
    val cancelCurrentRequest: () -> Unit = {
        activeToken?.cancel()
    }
    val sendCurrentInput = { sendContent(input) }
    LaunchedEffect(Unit) {
        inputFocusRequester.requestFocus()
    }
    ConversationStartupScrollEffect(history, listState)
    ConversationScrollOnChangeEffect(history, listState)
    ConversationResizeScrollEffect(panelSize, history, listState)
    ConversationQueueFlushEffect(
        sending = sending,
        inFlight = inFlight,
        queue = queue,
        agentManager = agentManager,
        inputFocusRequester = inputFocusRequester,
        onInputChange = { input = it },
        onSendingChange = { sending = it },
        onStatusChange = { status = it },
        onHistoryChange = { history = it },
        onActiveTokenChange = { activeToken = it },
    )
    var inputAreaHeight by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val animatedBottomPadding by animateDpAsState(
        targetValue = with(density) { inputAreaHeight.toDp() } + 8.dp,
        animationSpec = tween(200),
        label = "input-bottom-padding",
    )
    Box(modifier = Modifier.fillMaxSize().onSizeChanged { panelSize = it }) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    top = 4.dp,
                    bottom = animatedBottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ConversationMessageList(
                history = history,
                sending = sending,
                inFlight = inFlight,
                deletingMessageIds = deletingMessageIds,
                onDeleteMessage = { id ->
                    deletingMessageIds += id
                    scope.launch {
                        delay(DELETE_ANIMATION_DURATION_MS.toLong())
                        agentManager.deleteMessageById(id)
                        history = agentManager.getHistory()
                        deletingMessageIds -= id
                        status = "Message deleted"
                    }
                },
                onStatusChange = { status = it },
                onEditMessage = { editingId = it },
                sendContent = sendContent,
            )
        }
        ConversationScrollToBottomArea(
            isAtBottom = isAtBottom,
            history = history,
            listState = listState,
            scope = scope,
        )
        MessageQueueStrip(
            queue = queue,
            onSendNow = { msg ->
                queue.remove(msg.id)
                activeToken?.cancel()
                scope.launch {
                    delay(200)
                    executeSend(
                        content = msg.content,
                        agentManager = agentManager,
                        inFlight = inFlight,
                        inputFocusRequester = inputFocusRequester,
                        onInputChange = { input = it },
                        onSendingChange = { sending = it },
                        onStatusChange = { status = it },
                        onHistoryChange = { history = it },
                        onActiveTokenChange = { activeToken = it },
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
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .animateContentSize(animationSpec = tween(200))
                    .onSizeChanged { inputAreaHeight = it.height },
        ) {
            ConversationInputArea(
                input = input,
                sending = sending,
                onInputChange = { input = it },
                onSend = sendCurrentInput,
                onCancel = cancelCurrentRequest,
                onClear = {
                    handleClearConversation(
                        scope = scope,
                        modalRequester = modalRequester,
                        agentManager = agentManager,
                        activeToken = { activeToken },
                        onSendingChange = { sending = it },
                        onStatusChange = { status = it },
                        onHistoryRefresh = { history = agentManager.getHistory() },
                    )
                },
                inputFocusRequester = inputFocusRequester,
            )
        }
    }
    ConversationEditModal(
        editingId = editingId,
        history = history,
        agentManager = agentManager,
        onDismiss = { editingId = null },
        onHistoryRefresh = { history = agentManager.getHistory() },
    )
}
