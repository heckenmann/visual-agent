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
import de.heckenmann.visualagent.protocol.TodoChange
import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.protocol.TodoPort
import de.heckenmann.visualagent.protocol.TodoProgress
import de.heckenmann.visualagent.protocol.TodoResponseSnapshot
import de.heckenmann.visualagent.ui.todo.TodoResponseState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Presentation state for persisted todos and their canonical execution streams. */
@Stable
internal class ConversationTodoState {
    var todos: List<TodoItem> by mutableStateOf(emptyList())
    var deletedSnapshots: Map<String, TodoItem> by mutableStateOf(emptyMap())
    var responses: Map<String, TodoResponseState> by mutableStateOf(emptyMap())
    private var generation = 0L
    private var initialLoadPending = true
    private val changedDuringLoad = mutableSetOf<String>()
    private val removedDuringLoad = mutableSetOf<String>()
    private val progressedDuringLoad = mutableSetOf<String>()

    /** Retains the same response holder when details open before the first progress event. */
    fun responseFor(todoId: String): TodoResponseState =
        responses[todoId] ?: TodoResponseState().also { responses = responses + (todoId to it) }

    /** Captures the current presentation lifetime before beginning initial I/O. */
    fun initialLoadToken(): Long = generation

    /** Hydrates existing holders while retaining lifecycle and progress events received during I/O. */
    fun restoreInitialSnapshots(
        token: Long,
        loadedTodos: List<TodoItem>,
        loadedDeleted: List<TodoItem>,
        loadedResponses: List<TodoResponseSnapshot>,
    ) {
        if (token != generation || !initialLoadPending) return
        loadedResponses.forEach { snapshot ->
            if (snapshot.todoId !in progressedDuringLoad) {
                responseFor(snapshot.todoId).hydrateOutput(snapshot.text, snapshot.agentId, snapshot.attempts)
            }
        }
        todos =
            (loadedTodos.filterNot { it.id in changedDuringLoad } + todos.filter { it.id in changedDuringLoad })
                .distinctBy { it.id }
                .sortedBy { it.position }
        deletedSnapshots =
            loadedDeleted.filterNot { it.id in changedDuringLoad }.associateBy { it.id } +
            (loadedTodos + loadedDeleted).filter { it.id in removedDuringLoad }.associateBy { it.id } +
            deletedSnapshots.filterKeys { it in changedDuringLoad }
        initialLoadPending = false
        changedDuringLoad.clear()
        removedDuringLoad.clear()
        progressedDuringLoad.clear()
    }

    /** Applies a lifecycle event on Main and remembers its precedence over the initial snapshot. */
    fun applyChange(change: TodoChange) {
        val id = change.todoId ?: change.todo?.id ?: return
        if (initialLoadPending) changedDuringLoad += id
        if (change.removed) {
            if (initialLoadPending) removedDuringLoad += id
            val archivedTodo = change.todo ?: todos.firstOrNull { it.id == id }
            archivedTodo?.let { deletedSnapshots = deletedSnapshots + (id to it) }
            todos = todos.filterNot { it.id == id }
        } else {
            change.todo?.let { todo ->
                removedDuringLoad -= todo.id
                todos = (todos.filterNot { it.id == todo.id } + todo).sortedBy { it.position }
                deletedSnapshots = deletedSnapshots - todo.id
            }
        }
    }

    /** Preserves live execution state when an older persisted response finishes loading. */
    fun applyProgress(update: TodoProgress) {
        if (initialLoadPending && (!update.completed || update.delta.isNotEmpty())) progressedDuringLoad += update.todoId
        responseFor(update.todoId).apply(update.executionId, update.agentId, update.delta, update.completed, update.reviewing)
    }

    /** Clears the rendered todo state after the server removes all todo records. */
    fun clear() {
        generation++
        initialLoadPending = false
        changedDuringLoad.clear()
        removedDuringLoad.clear()
        progressedDuringLoad.clear()
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
    val progressUpdates = remember(todoPort) { Channel<TodoProgress>(Channel.UNLIMITED) }
    LaunchedEffect(todoPort) {
        val token = state.initialLoadToken()
        val loadedTodos = withContext(Dispatchers.IO) { todoPort.list() }
        val loadedDeleted = withContext(Dispatchers.IO) { todoPort.deletedSnapshots() }
        val loadedResponses =
            withContext(Dispatchers.IO) {
                todoPort.responseSnapshots((loadedTodos + loadedDeleted).map { it.id }.toSet())
            }
        state.restoreInitialSnapshots(token, loadedTodos, loadedDeleted, loadedResponses)
    }
    LaunchedEffect(progressUpdates) {
        for (update in progressUpdates) {
            state.applyProgress(update)
        }
    }
    DisposableEffect(todoPort, progressUpdates) {
        val todoHandle =
            todoPort.addListener { change ->
                scope.launch {
                    state.applyChange(change)
                    val contracted =
                        change.todo?.takeIf {
                            it.contract != null &&
                                it.status != de.heckenmann.visualagent.protocol.TodoState.IN_PROGRESS
                        }
                    if (contracted != null) {
                        val snapshot = withContext(Dispatchers.IO) { todoPort.responseSnapshots(setOf(contracted.id)) }.firstOrNull()
                        snapshot?.let { state.responseFor(it.todoId).restore(it.text, it.agentId, it.attempts) }
                    }
                    if (!conversationState.sending) {
                        val request = conversationState.beginLatestRequest()
                        val page = withContext(Dispatchers.IO) { conversationPort.latest() }
                        conversationState.applyLatest(request, page)
                    }
                }
            }
        val progressHandle =
            todoPort.addProgressListener { update ->
                progressUpdates.trySend(update)
            }
        onDispose {
            todoHandle.close()
            progressHandle.close()
            progressUpdates.close()
        }
    }
    return state
}
