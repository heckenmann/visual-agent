@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

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
    var streamedResponses by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val scope = rememberCoroutineScope()

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
    DisposableEffect(todoPort) {
        val todoHandle =
            todoPort.addListener { change ->
                if (lifecycle.closing) return@addListener
                scope.launch {
                    refreshTodos()
                    val todo = change.todo
                    if (todo != null && todo.status != TodoState.IN_PROGRESS) {
                        streamedResponses = streamedResponses - todo.id
                    }
                }
            }
        val progressHandle =
            todoPort.addProgressListener { update ->
                if (lifecycle.closing) return@addProgressListener
                scope.launch {
                    streamedResponses =
                        if (update.completed) {
                            streamedResponses - update.todoId
                        } else {
                            streamedResponses +
                                (
                                    update.todoId to
                                        streamedResponses[update.todoId].orEmpty() + update.delta
                                )
                        }
                }
            }
        onDispose {
            todoHandle.close()
            progressHandle.close()
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
                    todoPort.startAll()
                    refresh()
                },
            )
            ActionIconButton(
                icon = Icons.Filled.Stop,
                description = "Stop all todos",
                enabled = hasStoppableTodos,
                onClick = {
                    todoPort.stopAll()
                    refresh()
                },
            )
            ActionIconButton(
                icon = Icons.Filled.Add,
                description = "Add todo",
                onClick = {
                    modalRequester.request(
                        ComposeContentModal(title = "Add todo") { dismiss ->
                            TodoEditor(
                                todo = TodoItem(id = "", description = "", status = TodoState.PENDING),
                                agents = todoPort.agents(),
                                onCancel = dismiss,
                                onSave = { newDescription, newStatus, newAgentId ->
                                    val created = todoPort.add(newDescription)
                                    todoPort.updateStatus(created.id, newStatus)
                                    if (newAgentId != null) {
                                        todoPort.updateAssignedAgent(created.id, newAgentId)
                                    }
                                    refresh()
                                    dismiss()
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
                todoPort.reorder(reordered.map { it.id })
                refresh()
            },
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f).animateContentSize().verticalScroll(todoListScrollState),
        ) { _, todo, isDragging ->
            TodoRow(
                todo = todo,
                isNext = todo.id == nextTodoId,
                isDragging = isDragging,
                streamedResponse = streamedResponses[todo.id].orEmpty(),
                todoPort = todoPort,
                modalRequester = modalRequester,
                refresh = refresh,
            )
        }
    }
}
