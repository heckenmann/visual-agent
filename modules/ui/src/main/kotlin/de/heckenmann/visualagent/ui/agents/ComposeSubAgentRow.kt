@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.agents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.Agent
import de.heckenmann.visualagent.protocol.AgentPort
import de.heckenmann.visualagent.protocol.ProviderPort
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun SubAgentRow(
    agent: Agent,
    activeJobCount: Int,
    individuallyPaused: Boolean,
    globallyPaused: Boolean,
    agentPort: AgentPort,
    providerPort: ProviderPort,
    modalRequester: ComposeModalRequester,
    onStatusChanged: (String) -> Unit,
    refresh: () -> Unit,
    scope: CoroutineScope,
    onExecutionStateChanged: () -> Unit,
) {
    PanelContentCard(modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
        Text(agent.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(
            buildString {
                append(agent.status.name.labelizeEnumName())
                if (globallyPaused) append(" · globally paused")
                if (individuallyPaused) append(" · individually paused")
                append(" · active jobs $activeJobCount")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Text(agent.role, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActionIconButton(
                icon = if (individuallyPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                description = if (individuallyPaused) "Resume sub-agent" else "Pause sub-agent",
                onClick = {
                    scope.launch {
                        if (individuallyPaused) {
                            agentPort.resume(agent.id)
                            onStatusChanged("Resumed ${agent.name}")
                        } else {
                            agentPort.pause(agent.id)
                            onStatusChanged("Paused ${agent.name}")
                        }
                        onExecutionStateChanged()
                    }
                },
            )
            ActionIconButton(
                icon = Icons.Filled.History,
                description = "View sub-agent logs",
                onClick = {
                    modalRequester.requestInfo(
                        ComposeInfoModal(
                            title = "${agent.name} logs",
                            message = subAgentLogSummary(agent, activeJobCount),
                        ),
                    )
                },
            )
            ActionIconButton(
                icon = Icons.Filled.Edit,
                description = "Configure sub-agent details",
                onClick = {
                    modalRequester.request(
                        ComposeContentModal(title = "Configure ${agent.name}") { dismiss ->
                            SubAgentDetailsEditor(
                                agent = agent,
                                agentPort = agentPort,
                                providerPort = providerPort,
                                onSaved = {
                                    refresh()
                                    onStatusChanged("Saved ${it.name}")
                                    dismiss()
                                },
                            )
                        },
                    )
                },
            )
            ActionIconButton(
                icon = Icons.Filled.Delete,
                description = "Delete sub-agent",
                onClick = {
                    modalRequester.requestConfirmation(
                        ComposeConfirmationModal(
                            title = "Delete sub-agent?",
                            message = "Delete '${agent.name}' and its persisted configuration.",
                            confirmDescription = "Delete sub-agent",
                        ) {
                            agentPort.delete(agent.id)
                            refresh()
                        },
                    )
                },
            )
        }
    }
}

internal fun subAgentLogSummary(
    agent: Agent,
    activeJobCount: Int,
): String =
    buildString {
        appendLine("Status: ${agent.status}")
        appendLine("Active jobs: $activeJobCount")
        appendLine("Current task: ${agent.currentTask.orEmpty().ifBlank { "None" }}")
        appendLine("Current todo: ${agent.currentTodoId.orEmpty().ifBlank { "None" }}")
        appendLine()
        appendLine("Recent chat history")
        val recentMessages = agent.chatHistory.takeLast(12)
        if (recentMessages.isEmpty()) {
            appendLine("No recent chat history.")
        } else {
            recentMessages.forEachIndexed { index, message ->
                appendLine("${index + 1}. ${message.role.uppercase()}")
                appendLine(
                    message.content
                        .trim()
                        .ifBlank { "(empty)" }
                        .take(1200),
                )
                appendLine()
            }
        }
    }.trimEnd()
