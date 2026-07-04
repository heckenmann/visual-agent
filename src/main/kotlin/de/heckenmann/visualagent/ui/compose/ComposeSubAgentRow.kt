@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun SubAgentRow(
    agent: SubAgent,
    activeJobCount: Int,
    task: String,
    running: Boolean,
    agentManager: AgentManager,
    agentToolConfigService: AgentToolConfigService,
    toolRegistry: ToolRegistry,
    providerCatalogService: ProviderCatalogService,
    modalRequester: ComposeModalRequester,
    onRunningChanged: (Boolean) -> Unit,
    onStatusChanged: (String) -> Unit,
    refresh: () -> Unit,
    scope: CoroutineScope,
) {
    PanelContentCard(modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
        Text(agent.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "${agent.status.name.labelizeEnumName()} · active jobs $activeJobCount",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Text(agent.role, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActionIconButton(
                icon = Icons.Filled.PlayArrow,
                description = "Run sub-agent",
                enabled = task.isNotBlank() && !running,
                onClick = {
                    val requestedTask = task.trim()
                    if (requestedTask.isBlank()) return@ActionIconButton
                    onRunningChanged(true)
                    onStatusChanged("Running ${agent.name}...")
                    scope.launch {
                        runCatching { agentManager.runAgentJob(agent.id, requestedTask) }
                            .onSuccess { result ->
                                onStatusChanged("${result.agentName}: ${result.content.take(120)}")
                                refresh()
                            }.onFailure { error ->
                                onStatusChanged("Run failed for ${agent.name}: ${error.message}")
                                refresh()
                            }.also {
                                onRunningChanged(false)
                            }
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
                                agentManager = agentManager,
                                agentToolConfigService = agentToolConfigService,
                                toolRegistry = toolRegistry,
                                providerCatalogService = providerCatalogService,
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
                            agentManager.deleteAgent(agent.id)
                            refresh()
                        },
                    )
                },
            )
        }
    }
}

internal fun subAgentLogSummary(
    agent: SubAgent,
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
