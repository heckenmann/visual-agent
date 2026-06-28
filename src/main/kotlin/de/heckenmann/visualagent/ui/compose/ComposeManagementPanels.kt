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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.config.AppConfig
import kotlinx.coroutines.launch

@Composable
internal fun SubAgentsPanel(
    agentManager: AgentManager,
    agentToolConfigService: AgentToolConfigService,
    toolRegistry: ToolRegistry,
    modalRequester: ComposeModalRequester,
) {
    var agents by remember { mutableStateOf(agentManager.getSubAgents()) }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var task by remember { mutableStateOf("") }
    var runningAgentIds by remember { mutableStateOf(emptySet<String>()) }
    var status by remember { mutableStateOf("Ready") }
    val scope = rememberCoroutineScope()
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
        OutlinedTextField(
            value = task,
            onValueChange = { task = it },
            label = { Text("Task for selected sub-agent") },
            modifier = Modifier.fillMaxWidth(),
        )
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            agents.forEach { agent ->
                val activeJobCount = agentManager.getActiveJobCount(agent.id)
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Text(agent.name, color = Color(0xFFF8F8F2), fontWeight = FontWeight.SemiBold)
                    Text("${agent.status} · active jobs $activeJobCount", color = Color(0xFFBD93F9))
                    Text(agent.role, color = Color(0xFFE6E6E6), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionIconButton(
                            icon = Icons.Filled.PlayArrow,
                            description = "Run sub-agent",
                            enabled = task.isNotBlank() && agent.id !in runningAgentIds,
                            onClick = {
                                val requestedTask = task.trim()
                                if (requestedTask.isBlank()) return@ActionIconButton
                                runningAgentIds += agent.id
                                status = "Running ${agent.name}..."
                                scope.launch {
                                    runCatching { agentManager.runAgentJob(agent.id, requestedTask) }
                                        .onSuccess { result ->
                                            status = "${result.agentName}: ${result.content.take(120)}"
                                            refresh()
                                        }.onFailure { error ->
                                            status = "Run failed for ${agent.name}: ${error.message}"
                                            refresh()
                                        }.also {
                                            runningAgentIds -= agent.id
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
                                            onSaved = {
                                                refresh()
                                                status = "Saved ${it.name}"
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
        }
        PanelStatus(status)
    }
}

@Composable
private fun SubAgentDetailsEditor(
    agent: SubAgent,
    agentManager: AgentManager,
    agentToolConfigService: AgentToolConfigService,
    toolRegistry: ToolRegistry,
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
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = templateName,
                onValueChange = { templateName = it },
                label = { Text("Template") },
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = role,
            onValueChange = { role = it },
            label = { Text("Role") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = provider,
                onValueChange = { provider = it },
                label = { Text("Provider override") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model override") },
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = variant,
            onValueChange = { variant = it },
            label = { Text("Variant") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = temperature,
                onValueChange = { temperature = it },
                label = { Text("Temperature") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = topP,
                onValueChange = { topP = it },
                label = { Text("Top P") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = maxTokens,
                onValueChange = { maxTokens = it },
                label = { Text("Max tokens") },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = timeout,
                onValueChange = { timeout = it },
                label = { Text("Timeout") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = maxRetries,
                onValueChange = { maxRetries = it },
                label = { Text("Retries") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = memoryLimitMb,
                onValueChange = { memoryLimitMb = it },
                label = { Text("Memory MB") },
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = optionsText,
            onValueChange = { optionsText = it },
            label = { Text("Options key=value") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Tools", color = Color(0xFFF8F8F2), fontWeight = FontWeight.SemiBold)
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
                    Text(definition.id.value, color = Color(0xFFF8F8F2), fontWeight = FontWeight.SemiBold)
                    Text(definition.description, color = Color(0xFFE6E6E6), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        ActionIconButton(
            icon = Icons.Filled.Save,
            description = "Save sub-agent details",
            enabled = canSave,
            onClick = {
                val baseConfig = templateName.trim().takeIf(String::isNotEmpty)?.let(AgentConfig::fromTemplate) ?: agent.config
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
