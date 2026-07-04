@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoPriority
import de.heckenmann.visualagent.todo.TodoStatus

/**
 * Todo panel for creating, editing, and managing persisted todos.
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
) {
    var todos by remember { mutableStateOf(agentManager.getTodosFromDb()) }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(TodoPriority.MEDIUM) }
    var statusFilter by remember { mutableStateOf(ALL_TODO_STATUSES) }
    val refresh = {
        todos = agentManager.getTodosFromDb()
    }
    val visibleTodos =
        todos.filter { todo ->
            statusFilter == ALL_TODO_STATUSES || todo.status.name == statusFilter
        }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("New todo") },
                trailingIcon = {
                    ActionIconButton(
                        icon = Icons.Filled.Add,
                        description = "Add todo",
                        onClick = {
                            val text = description.trim()
                            if (text.isNotBlank()) {
                                agentManager.todoManager.add(text, priority)
                                description = ""
                                refresh()
                            }
                        },
                        enabled = description.isNotBlank(),
                        modifier = Modifier.size(32.dp),
                    )
                },
                modifier = Modifier.weight(1f),
            )
            PanelDropdownField(
                label = "Priority",
                selectedValue = priority.name,
                options = TodoPriority.entries.map { PanelSelectOption(it.name, it.name.labelizeEnumName()) },
                onSelected = { priority = TodoPriority.valueOf(it) },
                modifier = Modifier.weight(0.45f),
            )
        }
        PanelDropdownField(
            label = "Status filter",
            selectedValue = statusFilter,
            options =
                listOf(PanelSelectOption(ALL_TODO_STATUSES, "All statuses")) +
                    TodoStatus.entries.map { PanelSelectOption(it.name, it.name.labelizeEnumName()) },
            onSelected = { statusFilter = it },
        )
        Text(
            text = "Total ${todos.size} · showing ${visibleTodos.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (visibleTodos.isEmpty()) {
                PanelEmptyState(title = "No todos", body = "Add a task or change the status filter.")
            } else {
                visibleTodos.forEach { todo ->
                    TodoRow(todo, agentManager, modalRequester, refresh)
                }
            }
        }
    }
}

@Composable
private fun TodoRow(
    todo: Todo,
    agentManager: AgentManager,
    modalRequester: ComposeModalRequester,
    refresh: () -> Unit,
) {
    PanelContentCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
    ) {
        Text(todo.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "${todo.priority.name.labelizeEnumName()} · ${todo.status.name.labelizeEnumName()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelDropdownField(
                label = "Status",
                selectedValue = todo.status.name,
                options = TodoStatus.entries.map { PanelSelectOption(it.name, it.name.labelizeEnumName()) },
                onSelected = {
                    agentManager.todoManager.updateStatus(todo.id, TodoStatus.valueOf(it))
                    refresh()
                },
                modifier = Modifier.weight(1f),
            )
            ActionIconButton(
                icon = Icons.Filled.Edit,
                description = "Edit todo",
                onClick = {
                    modalRequester.request(
                        ComposeContentModal(title = "Edit todo") { dismiss ->
                            TodoEditor(
                                todo = todo,
                                onCancel = dismiss,
                                onSave = { updatedDescription, updatedPriority, updatedStatus ->
                                    agentManager.todoManager.update(todo.id, updatedDescription, updatedPriority)
                                    agentManager.todoManager.updateStatus(todo.id, updatedStatus)
                                    refresh()
                                    dismiss()
                                },
                            )
                        },
                    )
                },
            )
            ActionIconButton(
                icon = Icons.Filled.PlayArrow,
                description = "Start todo",
                enabled = todo.status != TodoStatus.IN_PROGRESS,
                onClick = {
                    agentManager.todoManager.updateStatus(todo.id, TodoStatus.IN_PROGRESS)
                    refresh()
                },
            )
            ActionIconButton(
                icon = Icons.Filled.Done,
                description = "Complete todo",
                enabled = todo.status != TodoStatus.COMPLETED,
                onClick = {
                    agentManager.todoManager.updateStatus(todo.id, TodoStatus.COMPLETED)
                    refresh()
                },
            )
            ActionIconButton(
                icon = Icons.Filled.Delete,
                description = "Delete todo",
                onClick = {
                    modalRequester.requestConfirmation(
                        ComposeConfirmationModal(
                            title = "Delete todo?",
                            message = "Delete '${todo.description}' from the persisted todo list.",
                            confirmDescription = "Delete todo",
                        ) {
                            agentManager.todoManager.remove(todo.id)
                            refresh()
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun TodoEditor(
    todo: Todo,
    onCancel: () -> Unit,
    onSave: (String, TodoPriority, TodoStatus) -> Unit,
) {
    var description by remember(todo.id) { mutableStateOf(todo.description) }
    var priority by remember(todo.id) { mutableStateOf(todo.priority) }
    var status by remember(todo.id) { mutableStateOf(todo.status) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelDropdownField(
                label = "Priority",
                selectedValue = priority.name,
                options = TodoPriority.entries.map { PanelSelectOption(it.name, it.name.labelizeEnumName()) },
                onSelected = { priority = TodoPriority.valueOf(it) },
                modifier = Modifier.weight(1f),
            )
            PanelDropdownField(
                label = "Status",
                selectedValue = status.name,
                options = TodoStatus.entries.map { PanelSelectOption(it.name, it.name.labelizeEnumName()) },
                onSelected = { status = TodoStatus.valueOf(it) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            ActionIconButton(icon = Icons.Filled.Close, description = "Cancel edit", onClick = onCancel)
            ActionIconButton(
                icon = Icons.Filled.Done,
                description = "Save todo",
                enabled = description.isNotBlank(),
                onClick = { onSave(description.trim(), priority, status) },
            )
        }
    }
}

internal const val ALL_TODO_STATUSES = "__all__"
