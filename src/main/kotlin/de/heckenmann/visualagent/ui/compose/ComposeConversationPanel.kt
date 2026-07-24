@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.FocusRequester
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
                val previousHistory = history
                history = agentManager.getHistory()
                if (sending || inFlight.state.value.totalActive > 0) {
                    val newSubAgentMessages =
                        history.filter { it.role == "sub_agent" && it !in previousHistory }
                    newSubAgentMessages.forEach { msg ->
                        queue.enqueue(
                            content = msg.content,
                            source = QueuedMessageSource.TODO_RETURN,
                        )
                    }
                }
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
    LaunchedEffect(sending, inFlight.state.value.totalActive) {
        if (!sending && inFlight.state.value.totalActive == 0 && queue.isNotEmpty && !queue.flushing) {
            queue.flushing = true
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
                                onInputChange = { input = it },
                                onSendingChange = { sending = it },
                                onStatusChange = { status = it },
                                onHistoryChange = { history = it },
                                onActiveTokenChange = { activeToken = it },
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
                            onInputChange = { input = it },
                            onSendingChange = { sending = it },
                            onStatusChange = { status = it },
                            onHistoryChange = { history = it },
                            onActiveTokenChange = { activeToken = it },
                        )
                    }
                }
            } finally {
                queue.flushing = false
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ConversationMessageList(
                    history = history,
                    sending = sending,
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
            if (!isAtBottom) {
                androidx.compose.animation.AnimatedVisibility(
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
        }
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
        ConversationInputArea(
            input = input,
            sending = sending,
            status = status,
            onInputChange = { input = it },
            onSend = sendCurrentInput,
            onCancel = cancelCurrentRequest,
            onHistoryReload = { history = agentManager.loadOlderHistory() },
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
    ConversationEditModal(
        editingId = editingId,
        history = history,
        agentManager = agentManager,
        onDismiss = { editingId = null },
        onHistoryRefresh = { history = agentManager.getHistory() },
    )
}
