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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoPriority
import de.heckenmann.visualagent.todo.TodoStatus
import kotlinx.coroutines.launch

@Composable
internal fun ConversationPanel(
    agentManager: AgentManager,
    modalRequester: ComposeModalRequester,
) {
    val scope = rememberCoroutineScope()
    val inputFocusRequester = remember { FocusRequester() }
    var history by remember { mutableStateOf(agentManager.getHistory()) }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready") }
    var sending by remember { mutableStateOf(false) }
    val sendContent: (String) -> Unit = { rawContent ->
        val content = rawContent.trim()
        if (content.isNotBlank() && !sending) {
            input = ""
            sending = true
            status = "Streaming..."
            history = history + Message("user", content) + Message("assistant", "")
            scope.launch {
                val streamedContent = StringBuilder()
                val result =
                    runCatching {
                        agentManager.streamMessage(content) { chunk ->
                            streamedContent.append(chunk)
                            history = history.dropLast(1) + Message("assistant", streamedContent.toString())
                        }
                    }
                result
                    .onSuccess {
                        history = agentManager.getHistory()
                        status = "Ready"
                    }.onFailure {
                        history = agentManager.getHistory()
                        status = "Error: ${it.message}"
                    }.also {
                        sending = false
                        inputFocusRequester.requestFocus()
                    }
            }
        }
    }
    val sendCurrentInput = {
        sendContent(input)
    }
    LaunchedEffect(Unit) {
        inputFocusRequester.requestFocus()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val visibleHistory = history.takeLast(40)
            val visibleOffset = history.size - visibleHistory.size
            if (visibleHistory.isEmpty()) {
                PanelEmptyState(
                    title = "No conversation yet",
                    body = "Send a message to start the main agent session.",
                )
            } else {
                visibleHistory.forEachIndexed { visibleIndex, message ->
                    MessageRow(
                        message = message,
                        canRetry = message.role == "assistant" && !sending,
                        onCopied = { status = "Copied ${message.role} message" },
                        onRetry = {
                            val absoluteIndex = visibleOffset + visibleIndex
                            val previousUserMessage = history.take(absoluteIndex).lastOrNull { it.role == "user" }
                            if (previousUserMessage == null) {
                                status = "No previous user message to retry"
                            } else {
                                status = "Retrying previous user message..."
                                sendContent(previousUserMessage.content)
                            }
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Message") },
            minLines = 2,
            trailingIcon = {
                ActionIconButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    description = "Send message",
                    onClick = sendCurrentInput,
                    enabled = !sending && input.isNotBlank(),
                    modifier = Modifier.size(32.dp),
                )
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(inputFocusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed) {
                            sendCurrentInput()
                            true
                        } else {
                            false
                        }
                    },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionIconButton(
                icon = Icons.Filled.History,
                description = "Load older history",
                onClick = { history = agentManager.loadOlderHistory() },
            )
            ActionIconButton(
                icon = Icons.Filled.Delete,
                description = "Clear conversation",
                onClick = {
                    modalRequester.requestConfirmation(
                        ComposeConfirmationModal(
                            title = "Clear conversation?",
                            message = "This removes the persisted conversation history for the current session.",
                            confirmDescription = "Clear conversation",
                        ) {
                            agentManager.clearHistory()
                            history = agentManager.getHistory()
                            status = "Conversation cleared"
                        },
                    )
                },
            )
        }
        PanelStatus(status)
    }
}

@Composable
private fun MessageRow(
    message: Message,
    canRetry: Boolean,
    onCopied: () -> Unit,
    onRetry: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    PanelContentCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 7.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message.role.uppercase(),
                color = if (message.role == "user") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            ActionIconButton(
                icon = Icons.Filled.ContentCopy,
                description = "Copy ${message.role} message",
                modifier = Modifier.size(28.dp),
                onClick = {
                    clipboard.setText(AnnotatedString(message.content))
                    onCopied()
                },
            )
            if (canRetry) {
                ActionIconButton(
                    icon = Icons.Filled.Refresh,
                    description = "Retry from previous user message",
                    modifier = Modifier.size(28.dp),
                    onClick = onRetry,
                )
            }
        }
        ComposeMarkdown(message.content)
    }
}

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

private const val ALL_TODO_STATUSES = "__all__"
