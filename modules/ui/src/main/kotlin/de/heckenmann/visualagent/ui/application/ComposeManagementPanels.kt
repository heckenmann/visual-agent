@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.application

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import de.heckenmann.visualagent.agent.SubAgentExecutionState
import de.heckenmann.visualagent.agent.addSubAgentExecutionListener
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.getSubAgentExecutionSnapshot
import de.heckenmann.visualagent.agent.pauseAllSubAgentsAsync
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.resumeAllSubAgentsAsync
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolRegistry
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

/**
 * Sub-agent management panel for creating, configuring, and deleting
 * worker agents.
 *
 * Use cases: UC-0000015, UC-0000016, UC-0000018, UC-0000051,
 * UC-0000071.
 *
 * @param agentManager Source of sub-agent lifecycle and job execution
 * @param agentToolConfigService Tool configuration service for sub-agents
 * @param toolRegistry Registry of available tools for configuration
 * @param providerCatalogService Provider catalog for inherited provider/model
 * @param modalRequester Modal requester used for destructive confirmations
 */
@Composable
internal fun SubAgentsPanel(
    agentManager: AgentManager,
    agentToolConfigService: AgentToolConfigService,
    toolRegistry: ToolRegistry,
    providerCatalogService: ProviderCatalogService,
    modalRequester: ComposeModalRequester,
    toolEventBus: ToolEventBus,
) {
    var agents by remember { mutableStateOf(agentManager.getSubAgents()) }
    var executionSnapshot by remember { mutableStateOf(agentManager.getSubAgentExecutionSnapshot()) }
    var status by remember { mutableStateOf("Ready") }
    val scope = rememberCoroutineScope()
    val refresh = {
        agents = agentManager.getSubAgents()
        executionSnapshot = agentManager.getSubAgentExecutionSnapshot()
    }
    DisposableEffect(agentManager) {
        val handle =
            agentManager.addSubAgentExecutionListener { snapshot ->
                scope.launch { executionSnapshot = snapshot }
            }
        onDispose { handle.close() }
    }
    ToolEventRefreshEffect(
        toolEventBus = toolEventBus,
        toolIds = setOf("agent:create", "agent:update", "agent:delete", "agent:list", "subagents:execution"),
        onRefresh = refresh,
    )
    val agentListScrollState = rememberScrollState()
    RegisterPanelVerticalScrollbar(agentListScrollState)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionIconButton(
                icon = if (executionSnapshot.globalState == SubAgentExecutionState.PAUSED) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                description =
                    if (executionSnapshot.globalState == SubAgentExecutionState.PAUSED) {
                        "Resume all sub-agents"
                    } else {
                        "Pause all sub-agents"
                    },
                onClick = {
                    scope.launch {
                        if (executionSnapshot.globalState == SubAgentExecutionState.PAUSED) {
                            agentManager.resumeAllSubAgentsAsync()
                        } else {
                            agentManager.pauseAllSubAgentsAsync()
                        }
                        executionSnapshot = agentManager.getSubAgentExecutionSnapshot()
                    }
                },
            )
            Text(
                if (executionSnapshot.globalState == SubAgentExecutionState.PAUSED) {
                    "All sub-agents paused"
                } else {
                    "Sub-agents running"
                },
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
            ActionIconButton(
                icon = Icons.Filled.Add,
                description = "Create sub-agent",
                onClick = {
                    modalRequester.request(
                        ComposeContentModal(title = "Create sub-agent") { dismiss ->
                            SubAgentCreationForm(
                                agentManager = agentManager,
                                onCreated = {
                                    refresh()
                                    status = "Created sub-agent"
                                    dismiss()
                                },
                                onCancel = dismiss,
                            )
                        },
                    )
                },
            )
        }
        Column(modifier = Modifier.weight(1f).verticalScroll(agentListScrollState)) {
            if (agents.isEmpty()) {
                PanelEmptyState(
                    title = "No sub-agents",
                    body = "Create a named role to delegate focused work from the main session.",
                )
            } else {
                agents.forEach { agent ->
                    SubAgentRow(
                        agent = agent,
                        activeJobCount = agentManager.getActiveJobCount(agent.id),
                        individuallyPaused = agent.id in executionSnapshot.pausedAgentIds,
                        globallyPaused = executionSnapshot.globalState == SubAgentExecutionState.PAUSED,
                        agentManager = agentManager,
                        agentToolConfigService = agentToolConfigService,
                        toolRegistry = toolRegistry,
                        providerCatalogService = providerCatalogService,
                        modalRequester = modalRequester,
                        onStatusChanged = { status = it },
                        refresh = refresh,
                        scope = scope,
                        onExecutionStateChanged = { executionSnapshot = agentManager.getSubAgentExecutionSnapshot() },
                    )
                }
            }
        }
        PanelStatus(status)
    }
}
