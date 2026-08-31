@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.agents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.Agent
import de.heckenmann.visualagent.protocol.AgentConfig
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

@Composable
internal fun SubAgentDetailsEditor(
    agent: Agent,
    agentPort: AgentPort,
    providerPort: ProviderPort,
    onSaved: (Agent) -> Unit,
) {
    var name by remember { mutableStateOf(agent.name) }
    var role by remember { mutableStateOf(agent.role) }
    var templateName by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf(agent.config.provider.orEmpty()) }
    var model by remember { mutableStateOf(agent.config.model.orEmpty()) }
    var variant by remember { mutableStateOf(agent.config.variant.orEmpty()) }
    var temperature by remember {
        mutableStateOf(
            agent.config.temperature
                ?.toString()
                .orEmpty(),
        )
    }
    var topP by remember {
        mutableStateOf(
            agent.config.topP
                ?.toString()
                .orEmpty(),
        )
    }
    var maxTokens by remember {
        mutableStateOf(
            agent.config.maxTokens
                ?.toString()
                .orEmpty(),
        )
    }
    var timeout by remember { mutableStateOf(agent.config.timeout.toString()) }
    var maxRetries by remember { mutableStateOf(agent.config.maxRetries.toString()) }
    var memoryLimitMb by remember { mutableStateOf(agent.config.memoryLimitMb.toString()) }
    var optionsText by remember { mutableStateOf(agent.config.options.toOptionsText()) }
    var selectedTools by remember {
        mutableStateOf(
            agent.config.tools?.toSet() ?: agentPort.toolsFor(agent.id),
        )
    }
    val toolDefinitions = remember(agentPort) { agentPort.toolDefinitions() }
    val providerOptions =
        remember(providerPort) {
            listOf(PanelSelectOption(INHERIT_SELECTION, "Inherit session provider")) +
                providerPort.enabledProviders().map { PanelSelectOption(it.id, "${it.name} (${it.id})") }
        }
    val modelOptions =
        remember(provider, model, providerPort) {
            if (provider.isBlank()) {
                listOf(PanelSelectOption(INHERIT_SELECTION, "Inherit session model"))
            } else {
                val catalogModels = providerPort.selectableModels(provider).map { PanelSelectOption(it.id, it.name) }
                val customModel =
                    model
                        .takeIf { current -> current.isNotBlank() && catalogModels.none { it.value == current } }
                        ?.let { PanelSelectOption(it, "Custom: $it") }
                listOf(PanelSelectOption(INHERIT_SELECTION, "Inherit provider default")) + catalogModels + listOfNotNull(customModel)
            }
        }
    val numericFieldsAreValid =
        timeout.toIntOrNull()?.let { it > 0 } == true &&
            maxRetries.toIntOrNull()?.let { it >= 0 } == true &&
            memoryLimitMb.toLongOrNull()?.let { it > 0 } == true &&
            temperature.optionalDoubleIsValid() &&
            topP.optionalDoubleIsValid() &&
            maxTokens.optionalIntIsValid() &&
            optionsText.toOptionsMapOrNull() != null
    val canSave = name.isNotBlank() && role.isNotBlank() && numericFieldsAreValid

    modalDialogLayout(
        body = {
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
                    selectedValue = templateName.ifBlank { KEEP_AGENT_CONFIG },
                    options =
                        listOf(PanelSelectOption(KEEP_AGENT_CONFIG, "Keep current")) +
                            AgentConfig.templates.keys
                                .sorted()
                                .map { PanelSelectOption(it, it.labelizeEnumName()) },
                    onSelected = { selected -> templateName = selected.takeUnless { it == KEEP_AGENT_CONFIG }.orEmpty() },
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = role,
                onValueChange = { role = it },
                label = { Text("Role") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                PanelDropdownField(
                    label = "Provider",
                    selectedValue = provider.ifBlank { INHERIT_SELECTION },
                    options = providerOptions,
                    onSelected = { selected ->
                        provider = selected.takeUnless { it == INHERIT_SELECTION }.orEmpty()
                        model = ""
                    },
                    modifier = Modifier.weight(1f),
                )
                PanelDropdownField(
                    label = "Model",
                    selectedValue = model.ifBlank { INHERIT_SELECTION },
                    options = modelOptions,
                    onSelected = { selected -> model = selected.takeUnless { it == INHERIT_SELECTION }.orEmpty() },
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = variant,
                onValueChange = { variant = it },
                label = { Text("Variant") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = temperature,
                    onValueChange = { temperature = it },
                    label = { Text("Temperature") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = topP,
                    onValueChange = { topP = it },
                    label = { Text("Top P") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                NumericPanelField(
                    value = maxTokens,
                    onValueChange = { maxTokens = it },
                    label = "Max tokens",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                NumericPanelField(
                    value = timeout,
                    onValueChange = { timeout = it },
                    label = "Timeout",
                    modifier = Modifier.weight(1f),
                )
                NumericPanelField(
                    value = maxRetries,
                    onValueChange = { maxRetries = it },
                    label = "Retries",
                    modifier = Modifier.weight(1f),
                )
                NumericPanelField(
                    value = memoryLimitMb,
                    onValueChange = { memoryLimitMb = it },
                    label = "Memory MB",
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = optionsText,
                onValueChange = { optionsText = it },
                label = { Text("Options key=value") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!numericFieldsAreValid) {
                Text(
                    "Check numeric values and options format.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Text("Tools", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            toolDefinitions.forEach { definition ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = definition.id in selectedTools,
                        onCheckedChange = { checked ->
                            selectedTools =
                                if (checked) {
                                    selectedTools + definition.id
                                } else {
                                    selectedTools - definition.id
                                }
                        },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(definition.id, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            definition.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        footer = {
            modalSaveButton(
                label = "Save changes",
                enabled = canSave,
                onClick = {
                    val baseConfig = templateName.takeIf(String::isNotBlank)?.let(AgentConfig::fromTemplate) ?: agent.config
                    val config =
                        baseConfig.copy(
                            timeout = timeout.toInt(),
                            maxRetries = maxRetries.toInt(),
                            memoryLimitMb = memoryLimitMb.toLong(),
                            provider = provider.trim().takeIf(String::isNotEmpty),
                            model = model.trim().takeIf(String::isNotEmpty),
                            variant = variant.trim().takeIf(String::isNotEmpty),
                            temperature = temperature.trim().takeIf(String::isNotEmpty)?.toDouble(),
                            topP = topP.trim().takeIf(String::isNotEmpty)?.toDouble(),
                            maxTokens = maxTokens.trim().takeIf(String::isNotEmpty)?.toInt(),
                            options = optionsText.toOptionsMapOrNull().orEmpty(),
                            tools = selectedTools.sorted(),
                        )
                    agentPort.update(agent.id, name.trim(), role.trim(), config)?.let(onSaved)
                },
            )
        },
    )
}
