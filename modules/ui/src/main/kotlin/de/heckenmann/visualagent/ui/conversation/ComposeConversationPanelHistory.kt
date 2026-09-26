package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ConversationMessage
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import de.heckenmann.visualagent.ui.todo.requestTodoResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Centers the empty prompt in the viewport above either composer placement. */
@Composable
internal fun conversationEmptyStateTopInset(
    inline: Boolean,
    inlineComposerHeightPx: Int,
    overlayBottomPadding: Dp,
): Dp = if (inline) with(LocalDensity.current) { inlineComposerHeightPx.toDp() + 4.dp } else overlayBottomPadding

/** Builds the rendered conversation timeline from the current panel state. */
internal fun buildConversationPanelTimeline(
    conversationState: ConversationUiState,
    streamingContent: String,
    streamingTurns: List<ConversationMessage>,
    requestActive: Boolean,
    todoState: ConversationTodoState,
    includeInlineComposer: Boolean,
): List<ConversationTimelineItem> =
    buildConversationTimeline(
        history = conversationState.history,
        pendingUserMessage = conversationState.pendingUserMessage,
        pendingUserEntryId = conversationState.pendingUserEntryId,
        streamingContent = streamingContent,
        streamingMessages = streamingTurns,
        streamingEntryId = conversationState.streamingEntryId,
        requestActive = requestActive,
        showOlderHistoryLoading =
            shouldShowOlderHistoryLoadingIndicator(
                conversationState.isLoadingOlder,
                conversationState.hasMoreHistory,
            ),
        includeInlineComposer = includeInlineComposer,
        todos = todoState.todos,
        deletedTodoSnapshots = todoState.deletedSnapshots,
        todoResponses = todoState.responses,
    )

/** Renders the conversation timeline while keeping panel orchestration separate. */
@Composable
internal fun ConversationPanelHistory(
    listState: LazyListState,
    timeline: List<ConversationTimelineItem>,
    conversationState: ConversationUiState,
    conversationPort: ConversationPort,
    modalRequester: ComposeModalRequester,
    todoState: ConversationTodoState,
    scope: CoroutineScope,
    sendContent: (String) -> Unit,
    inlineComposer: @Composable () -> Unit = {},
    bottomContentPadding: Dp = 4.dp,
    emptyStateTopInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.semantics { contentDescription = "Conversation history" },
        reverseLayout = true,
        contentPadding =
            PaddingValues(
                start = 8.dp,
                top = 4.dp,
                end = 8.dp,
                bottom = bottomContentPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        conversationTimeline(
            items = timeline,
            sending = conversationState.sending,
            deletingMessageIds = conversationState.deletingMessageIds,
            onDeleteMessage = { id ->
                conversationState.deletingMessageIds += id
                scope.launch {
                    delay(DELETE_ANIMATION_DURATION_MS.toLong())
                    val deleted = conversationPort.deleteMessage(id)
                    conversationState.replaceHistory(conversationPort.currentHistory())
                    conversationState.deletingMessageIds -= id
                    conversationState.status = if (deleted) "Message deleted" else "Message could not be deleted"
                }
            },
            onStatusChange = { conversationState.status = it },
            onEditMessage = { conversationState.editingId = it },
            sendContent = sendContent,
            inlineComposer = inlineComposer,
            emptyStateTopInset = emptyStateTopInset,
            onOpenTodoResponse = { todo, responseState ->
                modalRequester.requestTodoResponse(todo, responseState) {
                    todoState.todos.firstOrNull { current -> current.id == todo.id }
                        ?: todoState.deletedSnapshots[todo.id]
                }
            },
            shouldAnimateEntry = conversationState::shouldAnimateEntry,
            onMessageEntryRendered = conversationState::markEntryKnown,
        )
    }
}

/** Keeps the scroll-to-latest control binding out of the main conversation composition. */
@Composable
internal fun ConversationPanelScrollToLatest(
    isAtLatest: Boolean,
    hasNewMessages: Boolean,
    state: ConversationUiState,
    gateway: ConversationHistoryGateway,
    listState: LazyListState,
    scope: CoroutineScope,
    bottomInset: Dp = 0.dp,
) {
    ConversationScrollToLatestArea(
        isAtLatest,
        hasNewMessages,
        state,
        gateway,
        listState,
        scope,
        modifier = Modifier.padding(bottom = bottomInset),
    )
}
