package de.heckenmann.visualagent.ui.todo

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.LifecyclePort
import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.protocol.TodoPort
import de.heckenmann.visualagent.protocol.TodoProgress
import de.heckenmann.visualagent.protocol.TodoState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableColumn

/**
 * Todo panel for creating, editing, reordering, and managing persisted todos.
 *
 * Todos are shown in a drag-and-drop list where order determines which task is
 * processed next. The first pending todo is highlighted as the next item.
 *
 * Use cases: UC-0000013, UC-0000071.
 *
 * @param todoPort Source of todo persistence and updates
 * @param modalRequester Modal requester used for destructive confirmations
 */
@Composable
internal fun TodoPanel(
    todoPort: TodoPort,
    modalRequester: ComposeModalRequester,
    lifecycle: LifecyclePort,
) {
    var todos by remember { mutableStateOf<List<TodoItem>>(emptyList()) }
    var responseStates by remember { mutableStateOf<Map<String, TodoResponseState>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    val actions = rememberTodoActions(modalRequester)
    var agents by remember(todoPort) { mutableStateOf<List<de.heckenmann.visualagent.protocol.AgentSummary>>(emptyList()) }
    LaunchedEffect(todoPort, todos) {
        if (!lifecycle.closing) agents = withContext(Dispatchers.IO) { todoPort.agents() }
    }
    val progressUpdates = remember(todoPort) { Channel<TodoProgress>(Channel.UNLIMITED) }

    /** Loads persisted todos without blocking the Compose dispatcher. */
    suspend fun refreshTodos() {
        if (lifecycle.closing) return
        val refreshed =
            try {
                withContext(Dispatchers.IO) { todoPort.list() }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (!lifecycle.closing) throw failure
                return
            }
        if (!lifecycle.closing) todos = refreshed
    }
    val refresh: () -> Unit = {
        if (!lifecycle.closing) scope.launch { refreshTodos() }
    }
    LaunchedEffect(todoPort) { refreshTodos() }
    LaunchedEffect(progressUpdates) {
        for (update in progressUpdates) {
            val state = responseStates[update.todoId] ?: TodoResponseState()
            state.apply(update.executionId, update.agentId, update.delta, update.completed, update.reviewing)
            responseStates = responseStates + (update.todoId to state)
        }
    }
    DisposableEffect(todoPort, progressUpdates) {
        val todoHandle =
            todoPort.addListener { change ->
                if (lifecycle.closing) return@addListener
                scope.launch {
                    val changedTodo = change.todo
                    when {
                        change.reordered -> refreshTodos()
                        change.removed -> {
                            val removedId = change.todoId ?: change.todo?.id ?: return@launch
                            todos = todos.filterNot { it.id == removedId }
                            responseStates = responseStates - removedId
                        }
                        changedTodo != null -> {
                            todos = (todos.filterNot { it.id == changedTodo.id } + changedTodo).sortedBy(TodoItem::position)
                        }
                        else -> refreshTodos()
                    }
                }
            }
        val progressHandle =
            todoPort.addProgressListener { update ->
                if (lifecycle.closing) return@addProgressListener
                progressUpdates.trySend(update)
            }
        onDispose {
            todoHandle.close()
            progressHandle.close()
            progressUpdates.close()
        }
    }
    val nextTodoId = remember(todos) { todos.firstOrNull { it.status == TodoState.PENDING }?.id }
    val hasStartableTodos = todos.any { it.status == TodoState.PENDING || it.status == TodoState.CANCELLED }
    val hasStoppableTodos = todos.any { it.status == TodoState.PENDING || it.status == TodoState.IN_PROGRESS }
    val todoListScrollState = rememberScrollState()
    RegisterPanelVerticalScrollbar(todoListScrollState)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ActionIconButton(
                icon = Icons.Filled.PlayArrow,
                description = "Start all todos",
                enabled = hasStartableTodos,
                onClick = {
                    actions.submit("start-all", { todoPort.startAll() }, refresh)
                },
            )
            ActionIconButton(
                icon = Icons.Filled.Stop,
                description = "Stop all todos",
                enabled = hasStoppableTodos,
                onClick = {
                    actions.submit("stop-all", { todoPort.stopAll() }, refresh)
                },
            )
            ActionIconButton(
                icon = Icons.Filled.Add,
                description = "Add todo",
                onClick = {
                    var createdTodoId: String? = null
                    modalRequester.request(
                        ComposeContentModal(title = "Add todo") { dismiss ->
                            TodoEditor(
                                todo = TodoItem(id = "", description = "", status = TodoState.PENDING),
                                agents = agents,
                                onCancel = dismiss,
                                onSave = { newDescription, newStatus, newAgentId ->
                                    actions.submit("add", {
                                        val id = createdTodoId ?: todoPort.add(newDescription).id.also { createdTodoId = it }
                                        check(
                                            todoPort.update(
                                                de.heckenmann.visualagent.protocol.TodoUpdate(
                                                    todoId = id,
                                                    description = newDescription,
                                                    status = newStatus,
                                                    assignedAgentId = newAgentId,
                                                ),
                                            ),
                                        ) { "Todo was created, but its settings could not be saved. Retry to update the same todo." }
                                    }, {
                                        refresh()
                                        dismiss()
                                    })
                                },
                            )
                        },
                    )
                },
            )
        }
        Text(
            text = "Total ${todos.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ReorderableColumn(
            list = todos,
            onSettle = { fromIndex, toIndex ->
                val reordered = todos.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
                actions.submit(
                    "reorder",
                    { check(todoPort.reorder(reordered.map { it.id })) { "Todo order changed; please retry." } },
                    refresh,
                )
            },
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f).animateContentSize().verticalScroll(todoListScrollState),
        ) { _, todo, isDragging ->
            TodoRow(
                todo = todo,
                isNext = todo.id == nextTodoId,
                isDragging = isDragging,
                responseState = responseStates[todo.id] ?: remember(todo.id) { TodoResponseState() },
                currentTodo = { todos.firstOrNull { current -> current.id == todo.id } },
                todoPort = todoPort,
                agents = agents,
                actions = actions,
                modalRequester = modalRequester,
                refresh = refresh,
            )
        }
    }
}
