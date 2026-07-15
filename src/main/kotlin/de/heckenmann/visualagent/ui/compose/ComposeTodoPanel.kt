@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoStatus
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
    var statusFilter by remember { mutableStateOf(ALL_TODO_STATUSES) }
    val refresh = { todos = agentManager.getTodosFromDb() }
    DisposableEffect(todoEventBus) {
        val handle = todoEventBus.addListener { refresh() }
        onDispose { handle.close() }
    }
    ToolEventRefreshEffect(
        toolEventBus = toolEventBus,
        toolIds = setOf("todos", "agent:assign-todo", "agent:assign-next-todo", "agent:assign-all-todos"),
        onRefresh = refresh,
    )
    val visibleTodos = todos.filter { statusFilter == ALL_TODO_STATUSES || it.status.name == statusFilter }
    val nextTodoId = remember(visibleTodos) { visibleTodos.firstOrNull { it.status == TodoStatus.PENDING }?.id }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Filter",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PanelDropdownField(
                label = "Status",
                selectedValue = statusFilter,
                options =
                    listOf(PanelSelectOption(ALL_TODO_STATUSES, "All statuses")) +
                        TodoStatus.entries.map { PanelSelectOption(it.name, it.name.labelizeEnumName()) },
                onSelected = { statusFilter = it },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "Total ${todos.size} · showing ${visibleTodos.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ReorderableColumn(
            list = visibleTodos,
            onSettle = { fromIndex, toIndex ->
                val reordered = visibleTodos.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
                agentManager.todoManager.reorder(reordered.map { it.id })
                refresh()
            },
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f).animateContentSize(),
        ) { _, todo, isDragging ->
            TodoRow(
                todo = todo,
                isNext = todo.id == nextTodoId,
                isDragging = isDragging,
                agentManager = agentManager,
                modalRequester = modalRequester,
                refresh = refresh,
            )
        }
    }
}
