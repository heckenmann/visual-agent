@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    var history by remember { mutableStateOf(agentManager.getHistory()) }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            history.takeLast(40).forEach { message ->
                MessageRow(message)
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionIconButton(
                icon = Icons.AutoMirrored.Filled.Send,
                description = "Send message",
                onClick = {
                    val content = input.trim()
                    if (content.isBlank()) return@ActionIconButton
                    input = ""
                    status = "Sending..."
                    scope.launch {
                        runCatching { agentManager.sendMessage(content) }
                            .onSuccess {
                                history = agentManager.getHistory()
                                status = "Ready"
                            }.onFailure {
                                history = agentManager.getHistory()
                                status = "Error: ${it.message}"
                            }
                    }
                },
            )
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
private fun MessageRow(message: Message) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
    ) {
        Text(message.role.uppercase(), color = Color(0xFFFFB86C), fontWeight = FontWeight.SemiBold)
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
    val refresh = {
        todos = agentManager.getTodosFromDb()
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("New todo") },
                modifier = Modifier.weight(1f),
            )
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
            )
        }
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            todos.forEach { todo ->
                TodoRow(todo, agentManager, modalRequester, refresh)
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
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(todo.description, color = Color(0xFFF8F8F2), fontWeight = FontWeight.SemiBold)
        Text("${todo.priority} · ${todo.status}", color = Color(0xFFBD93F9))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
