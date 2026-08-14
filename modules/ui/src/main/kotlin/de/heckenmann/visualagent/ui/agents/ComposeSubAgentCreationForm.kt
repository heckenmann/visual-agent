@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.agents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.SubAgent
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

/**
 * Form for creating a persisted sub-agent from the creation dialog.
 *
 * @param agentManager Source of sub-agent persistence
 * @param onCreated Called after the new sub-agent has been persisted
 * @param onCancel Dismisses the creation dialog
 */
@Composable
internal fun SubAgentCreationForm(
    agentManager: AgentManager,
    onCreated: (SubAgent) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var templateName by remember { mutableStateOf(DEFAULT_AGENT_TEMPLATE) }
    val templateOptions =
        AgentConfig.TEMPLATES.keys
            .sorted()
            .map { PanelSelectOption(it, it.labelizeEnumName()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = role,
            onValueChange = { role = it },
            label = { Text("Role") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        PanelDropdownField(
            label = "Template",
            selectedValue = templateName,
            options = templateOptions,
            onSelected = { templateName = it },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            ActionIconButton(
                icon = Icons.Filled.Close,
                description = "Cancel create sub-agent",
                onClick = onCancel,
            )
            ActionIconButton(
                icon = Icons.Filled.Add,
                description = "Create sub-agent",
                enabled = name.isNotBlank() && role.isNotBlank(),
                onClick = {
                    onCreated(agentManager.createAgent(name.trim(), role.trim(), templateName))
                },
            )
        }
    }
}

internal const val DEFAULT_AGENT_TEMPLATE = "researcher"
