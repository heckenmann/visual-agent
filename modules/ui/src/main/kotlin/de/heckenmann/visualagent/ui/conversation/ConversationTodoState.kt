package de.heckenmann.visualagent.ui.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.protocol.TodoPort
import de.heckenmann.visualagent.ui.todo.TodoResponseState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Presentation state for persisted todos and their canonical execution streams. */
@Stable
internal class ConversationTodoState {
    var todos: List<TodoItem> by mutableStateOf(emptyList())
    var deletedSnapshots: Map<String, TodoItem> by mutableStateOf(emptyMap())
    var responses: Map<String, TodoResponseState> by mutableStateOf(emptyMap())
}

/** Loads todo state and subscribes the Conversation panel to server-owned todo events. */
@Composable
internal fun rememberConversationTodoState(
    todoPort: TodoPort,
    conversationPort: ConversationPort,
    conversationState: ConversationUiState,
): ConversationTodoState {
    val state = remember(todoPort) { ConversationTodoState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(todoPort) {
        state.todos = withContext(Dispatchers.IO) { todoPort.list() }
        state.deletedSnapshots = withContext(Dispatchers.IO) { todoPort.deletedSnapshots() }.associateBy { it.id }
    }
    DisposableEffect(todoPort) {
        val todoHandle =
            todoPort.addListener { change ->
                val removedSnapshot = change.todoId?.let { id -> state.todos.firstOrNull { todo -> todo.id == id } }
                scope.launch {
                    change.todo?.let { changedTodo ->
                        state.todos = (state.todos.filterNot { it.id == changedTodo.id } + changedTodo).sortedBy { it.position }
                    } ?: change.todoId?.let { removedId ->
                        removedSnapshot?.let { state.deletedSnapshots = state.deletedSnapshots + (removedId to it) }
                        state.todos = state.todos.filterNot { it.id == removedId }
                    }
                    if (!conversationState.sending) {
                        val history = withContext(Dispatchers.IO) { conversationPort.currentHistory() }
                        conversationState.replaceHistory(history)
                    }
                }
            }
        val progressHandle =
            todoPort.addProgressListener { update ->
                scope.launch {
                    val response = state.responses[update.todoId] ?: TodoResponseState()
                    response.apply(update.executionId, update.agentId, update.delta, update.completed)
                    state.responses = state.responses + (update.todoId to response)
                }
            }
        onDispose {
            todoHandle.close()
            progressHandle.close()
        }
    }
    return state
}
