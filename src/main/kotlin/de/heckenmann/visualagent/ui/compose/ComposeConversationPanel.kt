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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.conversation.WelcomeResult
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.error.ErrorMessageMapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Conversation panel with message history, streaming input, and todo actions.
 *
 * Use cases: UC-0000002, UC-0000003, UC-0000004, UC-0000045, UC-0000046,
 * UC-0000049, UC-0000071.
 *
 * @param agentManager Source of conversation history and message streaming
 * @param modalRequester Modal requester used for destructive confirmations
 * @param inFlight In-flight state holder for the active stream indicator
 */
@Composable
internal fun ConversationPanel(
    agentManager: AgentManager,
    modalRequester: ComposeModalRequester,
    inFlight: InFlightStateHolder,
    toolEventBus: ToolEventBus,
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
    val sendContent: (String) -> Unit = { rawContent ->
        val content = rawContent.trim()
        if (content.isNotBlank() && !sending) {
            input = ""
            sending = true
            status = "Streaming..."
            history = history + Message("user", content)
            val streamRequestId =
                java.util.UUID
                    .randomUUID()
                    .toString()
            val token = CancellationToken()
            activeToken = token
            inFlight.markStreamStart(streamRequestId)
            scope.launch {
                val streamedContent = StringBuilder()
                val result =
                    runCatching {
                        agentManager.streamMessage(content, token) { chunk ->
                            streamedContent.append(chunk)
                            history = history.dropLast(1) + Message("assistant", streamedContent.toString())
                        }
                    }
                result
                    .onSuccess {
                        history = agentManager.getHistory()
                        status = "Ready"
                    }.onFailure {
                        history = agentManager.getHistory()
                        val userError = ErrorMessageMapper.map(it)
                        status = "${userError.summary}: ${userError.detail}"
                    }.also {
                        inFlight.markStreamEnd(streamRequestId)
                        sending = false
                        activeToken = null
                        inputFocusRequester.requestFocus()
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
    LaunchedEffect(history.size) {
        if (isAtBottom && history.isNotEmpty()) {
            listState.animateScrollToItem(history.lastIndex)
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
                if (history.isEmpty()) {
                    item {
                        PanelEmptyState(
                            title = "No conversation yet",
                            body = "Send a message to start the main agent session.",
                        )
                    }
                } else {
                    itemsIndexed(history, key = { index, message -> message.id ?: "temp-$index" }) { index, message ->
                        val previousRole = history.getOrNull(index - 1)?.role
                        val isStreamingPlaceholder =
                            message.role == "assistant" && message.content.isBlank() && sending && index == history.lastIndex
                        val topPadding = if (previousRole == message.role) 2.dp else 10.dp
                        val onDelete: () -> Unit = {
                            message.id?.let { id ->
                                deletingMessageIds += id
                                scope.launch {
                                    delay(DELETE_ANIMATION_DURATION_MS.toLong())
                                    agentManager.deleteMessageById(id)
                                    history = agentManager.getHistory()
                                    deletingMessageIds -= id
                                    status = "Message deleted"
                                }
                            }
                        }
                        when (message.role) {
                            "tool" ->
                                ToolMessageRow(
                                    message = message,
                                    isDeleting = message.id in deletingMessageIds,
                                    onDelete = onDelete,
                                    modifier = Modifier.padding(top = topPadding),
                                )
                            "sub_agent" ->
                                SubAgentMessageRow(
                                    message = message,
                                    isDeleting = message.id in deletingMessageIds,
                                    onDelete = onDelete,
                                    modifier = Modifier.padding(top = topPadding),
                                )
                            else ->
                                MessageRow(
                                    message = message,
                                    isStreamingPlaceholder = isStreamingPlaceholder,
                                    canRetry = message.role == "assistant" && !sending && !isStreamingPlaceholder,
                                    canEdit = message.role == "user" && !sending,
                                    canDelete = message.id != null && message.role != "system" && message.id !in deletingMessageIds,
                                    isDeleting = message.id in deletingMessageIds,
                                    onCopied = { status = "Copied ${message.role} message" },
                                    onRetry = {
                                        val previousUserMessage = history.take(index).lastOrNull { it.role == "user" }
                                        if (previousUserMessage == null) {
                                            status = "No previous user message to retry"
                                        } else {
                                            status = "Retrying previous user message..."
                                            sendContent(previousUserMessage.content)
                                        }
                                    },
                                    onEdit = { editingId = message.id },
                                    onDelete = onDelete,
                                    modifier = Modifier.padding(top = topPadding),
                                )
                        }
                    }
                }
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
        ConversationInputArea(
            input = input,
            sending = sending,
            status = status,
            onInputChange = { input = it },
            onSend = sendCurrentInput,
            onCancel = cancelCurrentRequest,
            onHistoryReload = { history = agentManager.loadOlderHistory() },
            onClear = {
                modalRequester.requestConfirmation(
                    ComposeConfirmationModal(
                        title = "Clear conversation?",
                        message =
                            "This will stop any active request, cancel all running sub-agent jobs and open todos, " +
                                "then remove the persisted conversation history.",
                        confirmDescription = "Clear conversation",
                    ) {
                        scope.launch {
                            sending = true
                            status = "Stopping active work and clearing conversation..."
                            activeToken?.cancel()
                            agentManager.cancelAllRunningActions()
                            agentManager.cancelAllActiveTodos()
                            runCatching {
                                agentManager.clearHistory()
                                agentManager.addWelcomeMessageAfterReset()
                            }.onSuccess { result ->
                                status =
                                    when (result) {
                                        is WelcomeResult.Generated -> "Conversation cleared"
                                        is WelcomeResult.Fallback ->
                                            "Welcome could not be generated: ${result.error.message ?: result.error::class.simpleName.orEmpty()}"
                                    }
                            }.onFailure { error ->
                                status = "Welcome could not be generated: ${error.message ?: error::class.simpleName.orEmpty()}"
                            }
                            history = agentManager.getHistory()
                            sending = false
                        }
                    },
                )
            },
            inputFocusRequester = inputFocusRequester,
        )
    }
    if (editingId != null) {
        val message = history.find { it.id == editingId }
        if (message != null) {
            EditMessageModal(
                content = message.content,
                onDismiss = { editingId = null },
                onSave = { newContent ->
                    editingId?.let { id ->
                        agentManager.updateMessageContentById(id, newContent)
                        history = agentManager.getHistory()
                    }
                    editingId = null
                },
            )
        }
    }
}

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
