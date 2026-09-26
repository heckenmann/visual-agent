package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.CancellationToken
import de.heckenmann.visualagent.protocol.ClientImagePort
import de.heckenmann.visualagent.protocol.ConversationInputPlacement
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.ConversationSuggestionPort
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.TodoPort
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

internal typealias ConversationScrollStateObserver = (ConversationUiState, LazyListState) -> Unit

/**
 * Conversation panel with message history, streaming input, message queue, and todo actions.
 *
 * Use cases: UC-0000002, UC-0000003, UC-0000004, UC-0000045, UC-0000046, UC-0000049, UC-0000071.
 */
@Composable
internal fun ConversationPanel(
    modalRequester: ComposeModalRequester,
    inFlight: InFlightStateHolder,
    activityPort: ActivityPort,
    todoPort: TodoPort,
    conversationPort: ConversationPort,
    suggestionPort: ConversationSuggestionPort,
    settingsPort: SettingsPort,
    clientImagePort: ClientImagePort? = null,
    onScrollStateObserved: ConversationScrollStateObserver? = null,
) {
    val scope = rememberCoroutineScope()
    val inputFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val isAtLatest by remember(listState) { derivedStateOf { listState.conversationPosition().isAtLatest } }
    val conversationGateway = remember(conversationPort) { ProtocolConversationGateway(conversationPort) }
    val conversationState = rememberConversationUiState(emptyList())
    loadConversationHistory(conversationPort, conversationState)
    onScrollStateObserved?.invoke(conversationState, listState)
    val listScrollbarAdapter = rememberScrollbarAdapter(listState)
    RegisterPanelScrollbar(remember(listScrollbarAdapter) { ConversationReversedScrollbarAdapter(listScrollbarAdapter) })
    var activeToken by remember { mutableStateOf<CancellationToken?>(null) }
    var hasNewMessages by remember { mutableStateOf(false) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var overlayHeightPx by remember { mutableStateOf(0) }
    var inlineComposerHeightPx by remember { mutableStateOf(0) }
    val preferences = remember(conversationPort) { conversationPort.preferences() }
    var inputPlacement by remember { mutableStateOf(preferences.inputPlacement) }
    val streamingContent by conversationState.streaming.collectAsState()
    val streamingTurns by conversationState.streamingTurns.collectAsState()
    val contextReduced by conversationState.contextReduced.collectAsState()
    val suggestionController = rememberConversationSuggestionController(suggestionPort, settingsPort)
    val suggestionState by suggestionController.state.collectAsState()
    val todoState = rememberConversationTodoState(todoPort, conversationPort, conversationState)
    val queue = remember { MessageQueue() }
    LaunchedEffect(queue.messages.size) {
        suggestionController.onQueueSizeChanged(queue.messages.size)
    }
    LaunchedEffect(preferences.queueFlushMode) {
        queue.flushMode =
            try {
                QueueFlushMode.valueOf(preferences.queueFlushMode)
            } catch (_: IllegalArgumentException) {
                QueueFlushMode.ONE_BY_ONE
            }
    }
    val inputIsConversationMessage = inputPlacement == ConversationInputPlacement.CONVERSATION_MESSAGE
    val isRequestActive = inFlight.state.value.totalActive > 0
    val overlayBottomPadding = with(LocalDensity.current) { overlayHeightPx.toDp() + 8.dp }.coerceAtLeast(112.dp)
    val emptyStateTopInset = conversationEmptyStateTopInset(inputIsConversationMessage, inlineComposerHeightPx, overlayBottomPadding)
    val timeline =
        buildConversationPanelTimeline(
            conversationState,
            streamingContent,
            streamingTurns,
            isRequestActive,
            todoState,
            includeInlineComposer = inputIsConversationMessage,
        )
    ConversationHistoryPagingEffect(
        state = conversationState,
        listState = listState,
        gateway = conversationGateway,
    )
    ConversationActivityHistoryEffect(activityPort, conversationPort, conversationState)
    val sendContent =
        conversationSendAction(
            scope = scope,
            queue = queue,
            messageGateway = conversationGateway,
            inFlight = inFlight,
            conversationState = conversationState,
            suggestionController = suggestionController,
            contextReducedFlow = conversationState.contextReduced,
            onActiveTokenChange = { activeToken = it },
        )
    val clearConversation = {
        suggestionController.onUserInteraction()
        handleClearConversation(
            scope = scope,
            modalRequester = modalRequester,
            conversationPort = conversationPort,
            activeToken = { activeToken },
            onSendingChange = { value ->
                conversationState.sending = value
                suggestionController.onSendingChanged(value)
            },
            onStatusChange = { conversationState.status = it },
            onHistoryRefresh = { conversationState.resetHistory(conversationPort.currentHistory()) },
            onTodosCleared = todoState::clear,
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
        onNewContentWhileBrowsing = { hasNewMessages = true },
    )
    LaunchedEffect(isAtLatest) {
        if (isAtLatest) hasNewMessages = false
    }
    val hasConversationContent =
        conversationState.history.isNotEmpty() ||
            todoState.todos.isNotEmpty() ||
            todoState.deletedSnapshots.isNotEmpty() ||
            conversationState.pendingUserMessage != null ||
            streamingContent.isNotEmpty()
    ConversationResizeScrollEffect(viewportSize, hasConversationContent, listState, isAtLatest)
    ConversationQueueFlushEffect(
        sending = conversationState.sending,
        inFlight = inFlight,
        queue = queue,
        messageGateway = conversationGateway,
        onInputChange = { value ->
            conversationState.input = value
            suggestionController.onInputChanged(value)
        },
        onSendingChange = { value ->
            conversationState.sending = value
            suggestionController.onSendingChanged(value)
        },
        onStatusChange = { conversationState.status = it },
        onActiveTokenChange = { activeToken = it },
        onPendingUserMessageChange = { conversationState.pendingUserMessage = it },
        onPendingUserEntryIdChange = { conversationState.pendingUserEntryId = it },
        onStreamingEntryIdChange = { conversationState.streamingEntryId = it },
        onStreamCompletion = conversationState::completeStream,
        streamingFlow = conversationState.streaming,
    )
    val onInputPlacementChange =
        conversationInputPlacementChange(scope, conversationPort, listState) { placement -> inputPlacement = placement }
    val onComposerInputChange: (String) -> Unit = { value ->
        conversationState.input = value
        suggestionController.onInputChanged(value)
    }
    val onComposerSend: () -> Unit = { sendContent(conversationState.input) }
    val onComposerCancel: () -> Unit = {
        suggestionController.onUserInteraction()
        activeToken?.cancel()
    }
    CompositionLocalProvider(
        LocalConversationPort provides conversationPort,
        LocalClientImagePort provides clientImagePort,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().onSizeChanged { viewportSize = it }) {
                ConversationPanelHistory(
                    listState = listState,
                    timeline = timeline,
                    conversationState = conversationState,
                    conversationPort = conversationPort,
                    modalRequester = modalRequester,
                    todoState = todoState,
                    scope = scope,
                    sendContent = sendContent,
                    inlineComposer = {
                        Box(Modifier.fillMaxWidth().onSizeChanged { inlineComposerHeightPx = it.height }) {
                            ConversationInputCard(
                                input = conversationState.input,
                                sending = conversationState.sending,
                                isRequestActive = isRequestActive,
                                contextReduced = contextReduced,
                                onInputChange = onComposerInputChange,
                                onSend = onComposerSend,
                                onCancel = onComposerCancel,
                                onClear = clearConversation,
                                inputPlacement = inputPlacement,
                                onInputPlacementChange = onInputPlacementChange,
                                inputFocusRequester = inputFocusRequester,
                                ghostText = suggestionState.text,
                                ghostCursorVisible = suggestionState.cursorVisible,
                                onFocusChanged = suggestionController::onFocusChanged,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            )
                        }
                    },
                    bottomContentPadding = if (inputIsConversationMessage) 4.dp else overlayBottomPadding,
                    emptyStateTopInset = emptyStateTopInset,
                    modifier = Modifier.fillMaxSize(),
                )
                if (!inputIsConversationMessage) {
                    ConversationInputOverlay(
                        input = conversationState.input,
                        sending = conversationState.sending,
                        isRequestActive = isRequestActive,
                        contextReduced = contextReduced,
                        onInputChange = onComposerInputChange,
                        onSend = onComposerSend,
                        onCancel = onComposerCancel,
                        onClear = clearConversation,
                        inputPlacement = inputPlacement,
                        onInputPlacementChange = onInputPlacementChange,
                        inputFocusRequester = inputFocusRequester,
                        ghostText = suggestionState.text,
                        ghostCursorVisible = suggestionState.cursorVisible,
                        onFocusChanged = suggestionController::onFocusChanged,
                        onSizeChanged = { overlayHeightPx = it.height },
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .testTag("conversation-input-overlay"),
                    )
                }
                ConversationPanelQueueStrip(
                    queue = queue,
                    scope = scope,
                    inFlight = inFlight,
                    messageGateway = conversationGateway,
                    activeToken = { activeToken },
                    onInputChange = { value ->
                        conversationState.input = value
                        suggestionController.onInputChanged(value)
                    },
                    onSendingChange = { value ->
                        conversationState.sending = value
                        suggestionController.onSendingChanged(value)
                    },
                    onStatusChange = { conversationState.status = it },
                    onActiveTokenChange = { activeToken = it },
                    onPendingUserMessageChange = { conversationState.pendingUserMessage = it },
                    onPendingUserEntryIdChange = { conversationState.pendingUserEntryId = it },
                    onStreamingEntryIdChange = { conversationState.streamingEntryId = it },
                    onStreamCompletion = conversationState::completeStream,
                    streamingFlow = conversationState.streaming,
                )
                ConversationPanelScrollToLatest(
                    isAtLatest,
                    hasNewMessages,
                    conversationState,
                    conversationGateway,
                    listState,
                    scope,
                    if (inputIsConversationMessage) 0.dp else overlayBottomPadding + 8.dp,
                )
            }
            ConversationEditMessageOverlay(conversationState, conversationPort, modalRequester)
        }
    }
}
