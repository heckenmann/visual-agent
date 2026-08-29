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
import de.heckenmann.visualagent.protocol.TodoResponseSnapshot
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

    /** Clears the rendered todo state after the server removes all todo records. */
    fun clear() {
        todos = emptyList()
        deletedSnapshots = emptyMap()
        responses = emptyMap()
    }
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
        val loadedTodos = withContext(Dispatchers.IO) { todoPort.list() }
        val loadedDeleted = withContext(Dispatchers.IO) { todoPort.deletedSnapshots() }
        state.todos = loadedTodos
        state.deletedSnapshots = loadedDeleted.associateBy { it.id }
        state.responses =
            withContext(Dispatchers.IO) {
                todoPort.responseSnapshots((loadedTodos + loadedDeleted).map { it.id }.toSet())
            }.associate(TodoResponseSnapshot::toResponseEntry)
    }
    DisposableEffect(todoPort) {
        val todoHandle =
            todoPort.addListener { change ->
                val removedSnapshot = change.todoId?.let { id -> state.todos.firstOrNull { todo -> todo.id == id } }
                scope.launch {
                    if (change.removed) {
                        change.todoId?.let { removedId ->
                            val archivedTodo = change.todo ?: removedSnapshot
                            archivedTodo?.let { state.deletedSnapshots = state.deletedSnapshots + (removedId to it) }
                            state.todos = state.todos.filterNot { it.id == removedId }
                        }
                    } else {
                        change.todo?.let { changedTodo ->
                            state.todos = (state.todos.filterNot { it.id == changedTodo.id } + changedTodo).sortedBy { it.position }
                        }
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

private fun TodoResponseSnapshot.toResponseEntry(): Pair<String, TodoResponseState> =
    todoId to TodoResponseState().also { it.restore(text, agentId) }
