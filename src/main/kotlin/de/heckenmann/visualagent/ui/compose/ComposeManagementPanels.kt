@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolRegistry

/**
 * Sub-agent management panel for creating, running, configuring, and deleting
 * worker agents.
 *
 * Use cases: UC-0000015, UC-0000016, UC-0000018, UC-0000051, UC-0000052,
 * UC-0000071.
 *
 * @param agentManager Source of sub-agent lifecycle and job execution
 * @param agentToolConfigService Tool configuration service for sub-agents
 * @param toolRegistry Registry of available tools for configuration
 * @param providerCatalogService Provider catalog for inherited provider/model
 * @param modalRequester Modal requester used for destructive confirmations
 * @param inFlight In-flight state holder for running sub-agent jobs
 */
@Composable
internal fun SubAgentsPanel(
    agentManager: AgentManager,
    agentToolConfigService: AgentToolConfigService,
    toolRegistry: ToolRegistry,
    providerCatalogService: ProviderCatalogService,
    modalRequester: ComposeModalRequester,
    inFlight: InFlightStateHolder,
    toolEventBus: ToolEventBus,
) {
    var agents by remember { mutableStateOf(agentManager.getSubAgents()) }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var templateName by remember { mutableStateOf(DEFAULT_AGENT_TEMPLATE) }
    var task by remember { mutableStateOf("") }
    var runningAgentIds by remember { mutableStateOf(emptySet<String>()) }
    var status by remember { mutableStateOf("Ready") }
    val scope = rememberCoroutineScope()
    val refresh = {
        agents = agentManager.getSubAgents()
    }
    ToolEventRefreshEffect(
        toolEventBus = toolEventBus,
        toolIds = setOf("agent:create", "agent:update", "agent:delete", "agent:start", "agent:list"),
        onRefresh = refresh,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            PanelDropdownField(
                label = "Template",
                selectedValue = templateName,
                options =
                    AgentConfig.TEMPLATES.keys
                        .sorted()
                        .map { PanelSelectOption(it, it.labelizeEnumName()) },
                onSelected = { templateName = it },
                modifier = Modifier.weight(0.75f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = role,
                onValueChange = { role = it },
                label = { Text("Role") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            ActionIconButton(
                icon = Icons.Filled.Add,
                description = "Create sub-agent",
                enabled = name.isNotBlank() && role.isNotBlank(),
                onClick = {
                    agentManager.createAgent(name.trim(), role.trim(), templateName)
                    name = ""
                    role = ""
                    refresh()
                    status = "Created sub-agent"
                },
            )
        }
        OutlinedTextField(
            value = task,
            onValueChange = { task = it },
            label = { Text("Task for selected sub-agent") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
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
                        task = task,
                        running = agent.id in runningAgentIds,
                        agentManager = agentManager,
                        agentToolConfigService = agentToolConfigService,
                        toolRegistry = toolRegistry,
                        providerCatalogService = providerCatalogService,
                        modalRequester = modalRequester,
                        onRunningChanged = { running ->
                            runningAgentIds =
                                if (running) runningAgentIds + agent.id else runningAgentIds - agent.id
                            if (running) inFlight.markAgentStart(agent.id) else inFlight.markAgentEnd(agent.id)
                        },
                        onStatusChanged = { status = it },
                        refresh = refresh,
                        scope = scope,
                    )
                }
            }
        }
        PanelStatus(status)
    }
}

internal const val DEFAULT_AGENT_TEMPLATE = "researcher"
