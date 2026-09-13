package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import de.heckenmann.visualagent.ui.todo.requestTodoResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    inlineComposer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.semantics { contentDescription = "Conversation history" },
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
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
            onOpenTodoResponse = { todo, responseState ->
                modalRequester.requestTodoResponse(todo, responseState) {
                    todoState.todos.firstOrNull { current -> current.id == todo.id }
                        ?: todoState.deletedSnapshots[todo.id]
                }
            },
            shouldAnimateEntry = conversationState::shouldAnimateEntry,
            onMessageEntryRendered = conversationState::markEntryKnown,
            inlineComposer = inlineComposer,
        )
    }
}
