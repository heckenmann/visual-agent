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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.startAllTodos
import de.heckenmann.visualagent.agent.stopAllTodos
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoStatus
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
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableColumn

/**
 * Todo panel for creating, editing, reordering, and managing persisted todos.
 *
 * Todos are shown in a drag-and-drop list where order determines which task is
 * processed next. The first pending todo is highlighted as the next item.
 *
 * Use cases: UC-0000013, UC-0000071.
 *
 * @param agentManager Source of todo persistence and updates
 * @param modalRequester Modal requester used for destructive confirmations
 */
@Composable
internal fun TodoPanel(
    agentManager: AgentManager,
    modalRequester: ComposeModalRequester,
    todoEventBus: TodoEventBus,
    toolEventBus: ToolEventBus,
) {
    var todos by remember { mutableStateOf(agentManager.getTodosFromDb()) }
    var streamedResponses by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    val refresh = { todos = agentManager.getTodosFromDb() }
    DisposableEffect(todoEventBus) {
        val todoHandle =
            todoEventBus.addListener { change ->
                scope.launch {
                    refresh()
                    val todo = change.todo
                    if (todo != null && todo.status != TodoStatus.IN_PROGRESS) {
                        streamedResponses = streamedResponses - todo.id
                    }
                }
            }
        val progressHandle =
            todoEventBus.addProgressListener { update ->
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
    ToolEventRefreshEffect(
        toolEventBus = toolEventBus,
        toolIds = setOf("todos", "agent:assign-todo", "agent:assign-next-todo", "agent:assign-all-todos"),
        onRefresh = refresh,
    )
    val nextTodoId = remember(todos) { todos.firstOrNull { it.status == TodoStatus.PENDING }?.id }
    val hasStartableTodos = todos.any { it.status == TodoStatus.PENDING || it.status == TodoStatus.CANCELLED }
    val hasStoppableTodos = todos.any { it.status == TodoStatus.PENDING || it.status == TodoStatus.IN_PROGRESS }
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
                    agentManager.startAllTodos()
                    refresh()
                },
            )
            ActionIconButton(
                icon = Icons.Filled.Stop,
                description = "Stop all todos",
                enabled = hasStoppableTodos,
                onClick = {
                    agentManager.stopAllTodos()
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
                                todo = Todo(id = "", description = "", status = TodoStatus.PENDING),
                                agents = agentManager.getSubAgents(),
                                onCancel = dismiss,
                                onSave = { newDescription, newStatus, newAgentId ->
                                    val created = agentManager.todoManager.add(newDescription)
                                    agentManager.todoManager.updateStatus(created.id, newStatus)
                                    if (newAgentId != null) {
                                        agentManager.todoManager.updateAssignedAgent(created.id, newAgentId)
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
                agentManager.todoManager.reorder(reordered.map { it.id })
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
                agentManager = agentManager,
                modalRequester = modalRequester,
                refresh = refresh,
            )
        }
    }
}
