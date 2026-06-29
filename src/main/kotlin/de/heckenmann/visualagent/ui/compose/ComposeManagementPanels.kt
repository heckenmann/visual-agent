@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import kotlinx.coroutines.launch

@Composable
internal fun SubAgentsPanel(
    agentManager: AgentManager,
    agentToolConfigService: AgentToolConfigService,
    toolRegistry: ToolRegistry,
    providerCatalogService: ProviderCatalogService,
    modalRequester: ComposeModalRequester,
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

@Composable
private fun SubAgentRow(
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
    scope: kotlinx.coroutines.CoroutineScope,
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

@Composable
private fun SubAgentDetailsEditor(
    agent: SubAgent,
    agentManager: AgentManager,
    agentToolConfigService: AgentToolConfigService,
    toolRegistry: ToolRegistry,
    providerCatalogService: ProviderCatalogService,
    onSaved: (SubAgent) -> Unit,
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
            agent.config.tools?.toSet() ?: agentToolConfigService.toolsFor(agent).map { it.value }.toSet(),
        )
    }
    val toolDefinitions = remember(toolRegistry) { toolRegistry.toolDefinitions() }
    val providerOptions =
        remember(providerCatalogService) {
            listOf(PanelSelectOption(INHERIT_SELECTION, "Inherit session provider")) +
                providerCatalogService.enabledProviders().map { PanelSelectOption(it.id, "${it.name} (${it.id})") }
        }
    val modelOptions =
        remember(provider, model, providerCatalogService) {
            if (provider.isBlank()) {
                listOf(PanelSelectOption(INHERIT_SELECTION, "Inherit session model"))
            } else {
                val catalogModels = providerCatalogService.selectableModels(provider).map { PanelSelectOption(it.id, it.name) }
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

    Column(
        modifier =
            Modifier
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
                        AgentConfig.TEMPLATES.keys
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
                    checked = definition.id.value in selectedTools,
                    onCheckedChange = { checked ->
                        selectedTools =
                            if (checked) {
                                selectedTools + definition.id.value
                            } else {
                                selectedTools - definition.id.value
                            }
                    },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(definition.id.value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
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
        ActionIconButton(
            icon = Icons.Filled.Save,
            description = "Save sub-agent details",
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
                if (agentManager.updateAgent(agent.id, name.trim(), role.trim(), config)) {
                    onSaved(agent.copy(name = name.trim(), role = role.trim(), config = config))
                }
            },
        )
    }
}

private fun subAgentLogSummary(
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

private fun Map<String, String>.toOptionsText(): String =
    entries
        .sortedBy { it.key }
        .joinToString("\n") { "${it.key}=${it.value}" }

private fun String.toOptionsMapOrNull(): Map<String, String>? {
    val result = linkedMapOf<String, String>()
    lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .forEach { line ->
            val separatorIndex = line.indexOf('=')
            if (separatorIndex <= 0) return null
            val key = line.take(separatorIndex).trim()
            val value = line.drop(separatorIndex + 1).trim()
            if (key.isBlank()) return null
            result[key] = value
        }
    return result
}

private fun String.optionalDoubleIsValid(): Boolean = isBlank() || toDoubleOrNull() != null

private fun String.optionalIntIsValid(): Boolean = isBlank() || toIntOrNull() != null

private const val DEFAULT_AGENT_TEMPLATE = "researcher"
private const val KEEP_AGENT_CONFIG = "__keep__"
private const val INHERIT_SELECTION = "__inherit__"
