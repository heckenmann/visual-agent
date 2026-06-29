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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
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
                EmptyPanelState(
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
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 7.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x55282A36)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.role.uppercase(),
                    color = if (message.role == "user") Color(0xFFFFB86C) else Color(0xFF8BE9FD),
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
}

@Composable
internal fun TodoPanel(
    agentManager: AgentManager,
    modalRequester: ComposeModalRequester,
) {
    var todos by remember { mutableStateOf(agentManager.getTodosFromDb()) }
    var description by remember { mutableStateOf("") }
    val refresh = {
        todos = agentManager.getTodosFromDb()
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
                                agentManager.todoManager.add(text, TodoPriority.MEDIUM)
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
        }
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (todos.isEmpty()) {
                EmptyPanelState(title = "No todos", body = "Add a task or let the agent create todos during planning.")
            } else {
                todos.forEach { todo ->
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
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x55282A36)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(todo.description, color = Color(0xFFF8F8F2), fontWeight = FontWeight.SemiBold)
            Text("${todo.priority} · ${todo.status}", color = Color(0xFFBD93F9))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionIconButton(
                    icon = Icons.Filled.PlayArrow,
                    description = "Start todo",
                    onClick = {
                        agentManager.todoManager.updateStatus(todo.id, TodoStatus.IN_PROGRESS)
                        refresh()
                    },
                )
                ActionIconButton(
                    icon = Icons.Filled.Done,
                    description = "Complete todo",
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
}

@Composable
private fun EmptyPanelState(
    title: String,
    body: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x33282A36)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Color(0xFFF8F8F2), fontWeight = FontWeight.SemiBold)
            Text(body, color = Color(0xFFBFBBD0))
        }
    }
}
