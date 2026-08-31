@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.agents

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.heckenmann.visualagent.protocol.Agent
import de.heckenmann.visualagent.protocol.AgentConfig
import de.heckenmann.visualagent.protocol.AgentPort
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
 * @param agentPort Source of sub-agent persistence
 * @param onCreated Called after the new sub-agent has been persisted
 * @param onCancel Dismisses the creation dialog
 */
@Composable
internal fun SubAgentCreationForm(
    agentPort: AgentPort,
    onCreated: (Agent) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var templateName by remember { mutableStateOf(DEFAULT_AGENT_TEMPLATE) }
    val templateOptions =
        AgentConfig.templates.keys
            .sorted()
            .map { PanelSelectOption(it, it.labelizeEnumName()) }

    modalDialogLayout(
        body = {
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
        },
        footer = {
            modalSecondaryButton(label = "Cancel", onClick = onCancel)
            modalPrimaryButton(
                label = "Create sub-agent",
                enabled = name.isNotBlank() && role.isNotBlank(),
                onClick = {
                    onCreated(agentPort.create(name.trim(), role.trim(), templateName))
                },
            )
        },
    )
}

internal const val DEFAULT_AGENT_TEMPLATE = "researcher"
