@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.config.ConversationInputPlacement
import de.heckenmann.visualagent.todo.TodoEventBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
    RegisterPanelScrollbar(rememberScrollbarAdapter(listState))
    var history by remember { mutableStateOf(agentManager.getHistory()) }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready") }
    var sending by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var deletingMessageIds by remember { mutableStateOf(setOf<String>()) }
    var activeToken by remember { mutableStateOf<CancellationToken?>(null) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    var inputAreaHeight by remember { mutableStateOf(0) }
    var inputPlacement by remember { mutableStateOf(config.conversationInputPlacement) }
    var pendingUserMessage by remember { mutableStateOf<String?>(null) }
    val streamingFlow = remember { MutableStateFlow("") }
    val streamingContent by streamingFlow.collectAsState()
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
        derivedStateOf { !listState.canScrollBackward }
    }
    val isAtEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last != null && last.index == info.totalItemsCount - 1
        }
    }
    var isLoadingOlder by remember { mutableStateOf(false) }
    var hasMoreHistory by remember { mutableStateOf(true) }
    ConversationOlderHistoryLoader(
        isAtEnd = isAtEnd && !sending,
        history = history,
        listState = listState,
        agentManager = agentManager,
        onHistoryChange = { history = it },
        onLoadStateChange = { isLoadingOlder = it },
        onHasMoreHistoryChange = { hasMoreHistory = it },
    )
    DisposableEffect(toolEventBus) {
        val handle =
            toolEventBus.addListener { event ->
                if (event.phase == ToolCallPhase.FINISHED && !sending) {
                    history = agentManager.getHistory()
                }
            }
        onDispose { handle.close() }
    }
    DisposableEffect(todoEventBus) {
        val handle =
            todoEventBus.addListener {
                if (!sending) {
                    history = agentManager.getHistory()
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
                        onPendingUserMessageChange = { pendingUserMessage = it },
                        streamingFlow = streamingFlow,
                    )
                }
            }
        }
    }
    val cancelCurrentRequest: () -> Unit = {
        activeToken?.cancel()
    }
    val sendCurrentInput = { sendContent(input) }
    val clearConversation = {
        handleClearConversation(
            scope = scope,
            modalRequester = modalRequester,
            agentManager = agentManager,
            activeToken = { activeToken },
            onSendingChange = { sending = it },
            onStatusChange = { status = it },
            onHistoryRefresh = { history = agentManager.getHistory() },
        )
    }
    LaunchedEffect(Unit) {
        inputFocusRequester.requestFocus()
    }
    ConversationStartupScrollEffect(history, listState)
    ConversationScrollOnChangeEffect(history, listState, pendingUserMessage, streamingContent)
    val hasConversationContent = history.isNotEmpty() || pendingUserMessage != null || streamingContent.isNotEmpty()
    ConversationResizeScrollEffect(panelSize, inputAreaHeight, hasConversationContent, listState)
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
        onPendingUserMessageChange = { pendingUserMessage = it },
        streamingFlow = streamingFlow,
    )
    val density = LocalDensity.current
    val inputIsConversationMessage = inputPlacement == ConversationInputPlacement.CONVERSATION_MESSAGE
    val onInputPlacementChange: (ConversationInputPlacement) -> Unit = { placement ->
        inputPlacement = placement
        config.conversationInputPlacement = placement
        scope.launch { persistConversationInputPlacement(config) }
    }
    val animatedBottomPadding by animateDpAsState(
        targetValue = with(density) { inputAreaHeight.toDp() } + 8.dp,
        animationSpec = tween(200),
        label = "input-bottom-padding",
    )
    Box(modifier = Modifier.fillMaxSize().onSizeChanged { panelSize = it }) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().semantics { contentDescription = "Conversation history" },
            reverseLayout = true,
            contentPadding =
                PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    top = 4.dp,
                    bottom = if (inputIsConversationMessage) 8.dp else animatedBottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (inputIsConversationMessage) {
                item(key = "conversation-input") {
                    ConversationInputCard(
                        input = input,
                        sending = sending,
                        onInputChange = { input = it },
                        onSend = sendCurrentInput,
                        onCancel = cancelCurrentRequest,
                        onClear = clearConversation,
                        inputPlacement = inputPlacement,
                        onInputPlacementChange = onInputPlacementChange,
                        inputFocusRequester = inputFocusRequester,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (isLoadingOlder) {
                item(key = "loading-older") { OlderHistoryLoadingIndicator() }
            }
            ConversationMessageList(
                history = history.reversed(),
                sending = sending,
                inFlight = inFlight,
                pendingUserMessage = pendingUserMessage,
                streamingContent = streamingContent,
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
        ConversationPanelQueueStrip(
            queue,
            scope,
            inFlight,
            agentManager,
            inputFocusRequester,
            { activeToken },
            { input = it },
            { sending = it },
            { status = it },
            { history = it },
            { activeToken = it },
            { pendingUserMessage = it },
            streamingFlow,
        )
        if (!inputIsConversationMessage) {
            ConversationInputCard(
                input = input,
                sending = sending,
                onInputChange = { input = it },
                onSend = sendCurrentInput,
                onCancel = cancelCurrentRequest,
                onClear = clearConversation,
                inputPlacement = inputPlacement,
                onInputPlacementChange = onInputPlacementChange,
                inputFocusRequester = inputFocusRequester,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                onSizeChanged = { inputAreaHeight = it.height },
            )
        }
        ConversationPanelOverlays(
            isAtBottom,
            listState,
            scope,
            agentManager,
            inputAreaHeight,
            editingId,
            history,
            { history = agentManager.getHistory() },
            { editingId = null },
        )
    }
}
