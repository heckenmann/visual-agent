@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onSizeChanged
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal typealias ConversationScrollStateObserver = (ConversationUiState, LazyListState) -> Unit

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
    onScrollStateObserved: ConversationScrollStateObserver? = null,
) {
    val scope = rememberCoroutineScope()
    val inputFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val isAtLatest by remember(listState) { derivedStateOf { listState.conversationPosition().isAtLatest } }
    val conversationGateway = remember(agentManager) { AgentManagerConversationGateway(agentManager) }
    val conversationState = rememberConversationUiState(agentManager.getHistory())
    onScrollStateObserved?.invoke(conversationState, listState)
    RegisterPanelScrollbar(rememberScrollbarAdapter(listState))
    var activeToken by remember { mutableStateOf<CancellationToken?>(null) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var inputPlacement by remember { mutableStateOf(config.conversationInputPlacement) }
    val streamingContent by conversationState.streaming.collectAsState()
    val queue = remember { MessageQueue() }
    LaunchedEffect(config.queueFlushMode) {
        queue.flushMode =
            try {
                QueueFlushMode.valueOf(config.queueFlushMode)
            } catch (_: IllegalArgumentException) {
                QueueFlushMode.ONE_BY_ONE
            }
    }
    val inputIsConversationMessage = inputPlacement == ConversationInputPlacement.CONVERSATION_MESSAGE
    val showWaitingIndicator = inFlight.state.value.totalActive > 0 && streamingContent.isEmpty()
    val timeline =
        buildConversationTimeline(
            history = conversationState.history,
            pendingUserMessage = conversationState.pendingUserMessage,
            streamingContent = streamingContent,
            showWaitingIndicator = showWaitingIndicator,
            showOlderHistoryLoading =
                shouldShowOlderHistoryLoadingIndicator(
                    conversationState.isLoadingOlder,
                    conversationState.hasMoreHistory,
                ),
            includeInlineComposer = inputIsConversationMessage,
        )
    ConversationHistoryPagingEffect(
        state = conversationState,
        listState = listState,
        gateway = conversationGateway,
    )
    DisposableEffect(toolEventBus) {
        val handle =
            toolEventBus.addListener { event ->
                if (event.phase == ToolCallPhase.FINISHED && !conversationState.sending) {
                    conversationState.replaceHistory(agentManager.getHistory())
                }
            }
        onDispose { handle.close() }
    }
    DisposableEffect(todoEventBus) {
        val handle =
            todoEventBus.addListener {
                if (!conversationState.sending) {
                    conversationState.replaceHistory(agentManager.getHistory())
                }
            }
        onDispose { handle.close() }
    }
    val sendContent: (String) -> Unit = { rawContent ->
        val content = rawContent.trim()
        if (content.isNotBlank()) {
            if (conversationState.sending) {
                queue.enqueue(content, QueuedMessageSource.USER)
                conversationState.status = "Queued (${queue.size})"
            } else {
                scope.launch {
                    executeSend(
                        content = content,
                        messageGateway = conversationGateway,
                        inFlight = inFlight,
                        inputFocusRequester = inputFocusRequester,
                        onInputChange = { conversationState.input = it },
                        onSendingChange = { conversationState.sending = it },
                        onStatusChange = { conversationState.status = it },
                        onHistoryChange = conversationState::replaceHistory,
                        onActiveTokenChange = { activeToken = it },
                        onPendingUserMessageChange = { conversationState.pendingUserMessage = it },
                        streamingFlow = conversationState.streaming,
                    )
                }
            }
        }
    }
    val clearConversation = {
        handleClearConversation(
            scope = scope,
            modalRequester = modalRequester,
            agentManager = agentManager,
            activeToken = { activeToken },
            onSendingChange = { conversationState.sending = it },
            onStatusChange = { conversationState.status = it },
            onHistoryRefresh = { conversationState.replaceHistory(agentManager.getHistory()) },
        )
    }
    LaunchedEffect(Unit) {
        inputFocusRequester.requestFocus()
    }
    ConversationStartupScrollEffect(conversationState.history, listState)
    ConversationScrollOnChangeEffect(
        conversationState.history,
        listState,
        conversationState.pendingUserMessage,
        streamingContent,
        isAtLatest,
    )
    val hasConversationContent =
        conversationState.history.isNotEmpty() || conversationState.pendingUserMessage != null || streamingContent.isNotEmpty()
    ConversationResizeScrollEffect(viewportSize, hasConversationContent, listState, isAtLatest)
    ConversationQueueFlushEffect(
        sending = conversationState.sending,
        inFlight = inFlight,
        queue = queue,
        messageGateway = conversationGateway,
        inputFocusRequester = inputFocusRequester,
        onInputChange = { conversationState.input = it },
        onSendingChange = { conversationState.sending = it },
        onStatusChange = { conversationState.status = it },
        onHistoryChange = conversationState::replaceHistory,
        onActiveTokenChange = { activeToken = it },
        onPendingUserMessageChange = { conversationState.pendingUserMessage = it },
        streamingFlow = conversationState.streaming,
    )
    val onInputPlacementChange: (ConversationInputPlacement) -> Unit = { placement ->
        inputPlacement = placement
        config.conversationInputPlacement = placement
        scope.launch { persistConversationInputPlacement(config) }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth().onSizeChanged { viewportSize = it }) {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = "Conversation history" },
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ConversationTimeline(
                    items = timeline,
                    sending = conversationState.sending,
                    deletingMessageIds = conversationState.deletingMessageIds,
                    onDeleteMessage = { id ->
                        conversationState.deletingMessageIds += id
                        scope.launch {
                            delay(DELETE_ANIMATION_DURATION_MS.toLong())
                            agentManager.deleteMessageById(id)
                            conversationState.replaceHistory(agentManager.getHistory())
                            conversationState.deletingMessageIds -= id
                            conversationState.status = "Message deleted"
                        }
                    },
                    onStatusChange = { conversationState.status = it },
                    onEditMessage = { conversationState.editingId = it },
                    sendContent = sendContent,
                    inlineComposer = {
                        ConversationInputCard(
                            input = conversationState.input,
                            sending = conversationState.sending,
                            onInputChange = { conversationState.input = it },
                            onSend = { sendContent(conversationState.input) },
                            onCancel = { activeToken?.cancel() },
                            onClear = clearConversation,
                            inputPlacement = inputPlacement,
                            onInputPlacementChange = onInputPlacementChange,
                            inputFocusRequester = inputFocusRequester,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                )
            }
            ConversationPanelQueueStrip(
                queue,
                scope,
                inFlight,
                conversationGateway,
                inputFocusRequester,
                { activeToken },
                { conversationState.input = it },
                { conversationState.sending = it },
                { conversationState.status = it },
                conversationState::replaceHistory,
                { activeToken = it },
                { conversationState.pendingUserMessage = it },
                conversationState.streaming,
            )
            ConversationScrollToLatestArea(
                isAtLatest = isAtLatest,
                state = conversationState,
                gateway = conversationGateway,
                listState = listState,
                scope = scope,
            )
        }
        if (!inputIsConversationMessage) {
            ConversationInputCard(
                input = conversationState.input,
                sending = conversationState.sending,
                onInputChange = { conversationState.input = it },
                onSend = { sendContent(conversationState.input) },
                onCancel = { activeToken?.cancel() },
                onClear = clearConversation,
                inputPlacement = inputPlacement,
                onInputPlacementChange = onInputPlacementChange,
                inputFocusRequester = inputFocusRequester,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
        ConversationEditModal(
            editingId = conversationState.editingId,
            history = conversationState.history,
            agentManager = agentManager,
            onDismiss = { conversationState.editingId = null },
            onHistoryRefresh = { conversationState.replaceHistory(agentManager.getHistory()) },
        )
    }
}
