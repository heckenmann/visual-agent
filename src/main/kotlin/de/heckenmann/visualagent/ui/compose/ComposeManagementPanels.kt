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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.config.AppConfig

@Composable
internal fun SubAgentsPanel(
    agentManager: AgentManager,
    modalRequester: ComposeModalRequester,
) {
    var agents by remember { mutableStateOf(agentManager.getSubAgents()) }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    val refresh = {
        agents = agentManager.getSubAgents()
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.weight(0.7f),
            )
            OutlinedTextField(
                value = role,
                onValueChange = { role = it },
                label = { Text("Role") },
                modifier = Modifier.weight(1f),
            )
            ActionIconButton(
                icon = Icons.Filled.Add,
                description = "Create sub-agent",
                onClick = {
                    if (name.isNotBlank() && role.isNotBlank()) {
                        agentManager.createAgent(name.trim(), role.trim())
                        name = ""
                        role = ""
                        refresh()
                    }
                },
            )
        }
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            agents.forEach { agent ->
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Text(agent.name, color = Color(0xFFF8F8F2), fontWeight = FontWeight.SemiBold)
                    Text("${agent.status} · active jobs ${agentManager.getActiveJobCount(agent.id)}", color = Color(0xFFBD93F9))
                    Text(agent.role, color = Color(0xFFE6E6E6), maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    }
}

@Composable
internal fun SettingsPanel(config: AppConfig) {
    var provider by remember { mutableStateOf(config.normalizedProvider()) }
    var model by remember { mutableStateOf(config.activeModel()) }
    var theme by remember { mutableStateOf(config.theme) }
    var fontSize by remember { mutableStateOf(config.fontSize.toString()) }
    var status by remember { mutableStateOf("Settings are DB-backed after startup") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        OutlinedTextField(
            value = provider,
            onValueChange = { provider = it },
            label = { Text("Provider") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = theme, onValueChange = { theme = it }, label = { Text("Theme") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = fontSize,
            onValueChange = { fontSize = it },
            label = { Text("Font size") },
            modifier = Modifier.fillMaxWidth(),
        )
        ActionIconButton(
            icon = Icons.Filled.Save,
            description = "Save settings",
            onClick = {
                config.llmProvider = provider.trim().ifBlank { "ollama" }
                config.setActiveModel(model.trim().ifBlank { config.activeModel() })
                config.theme = theme.trim().ifBlank { "Dracula" }
                config.fontSize = fontSize.toIntOrNull()?.coerceIn(10, 28) ?: config.fontSize
                config.save()
                status = "Saved provider=${config.normalizedProvider()} model=${config.activeModel()}"
            },
        )
        PanelStatus(status)
    }
}
