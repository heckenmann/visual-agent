@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.codex.CodexCliAccountService
import de.heckenmann.visualagent.agent.codex.CodexCliProvider
import de.heckenmann.visualagent.agent.codex.CodexCliUpdateStatus
import de.heckenmann.visualagent.agent.codex.CodexCliVersionInfo
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderProfile
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ProviderProfileEditor(
    initial: ProviderProfileFormState,
    existing: ProviderProfile?,
    canDisable: Boolean,
    codexCliAccountService: CodexCliAccountService? = null,
    onCancel: () -> Unit,
    onSave: (ProviderProfile) -> Unit,
) {
    var state by remember { mutableStateOf(initial) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var accountStatus by remember { mutableStateOf("Checking Codex login status...") }
    var versionStatus by remember { mutableStateOf("Installed: checking...\nLatest: checking...") }
    var pendingAccountAction by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val explicitPath = state.codexExecutablePath()
    val validation = state.validationError()
    val saveDescription = if (existing == null) "Create provider" else "Save provider changes"
    LaunchedEffect(state.adapter, explicitPath) {
        if (state.adapter == ProviderAdapter.CODEX_CLI) {
            accountStatus = codexCliAccountService?.status(explicitPath)?.displayText() ?: "Codex account service unavailable"
            versionStatus = codexCliAccountService?.versionInfo(explicitPath)?.displayText() ?: "Codex version service unavailable"
        }
    }
    Column(
        modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Configure the provider connection. Model selection is managed separately.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = state.name,
            onValueChange = { state = state.copy(name = it) },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        PanelDropdownField(
            label = "Adapter",
            selectedValue = state.adapter.name,
            options = ProviderAdapter.entries.map { PanelSelectOption(it.name, it.name) },
            onSelected = { selected ->
                val adapter = ProviderAdapter.valueOf(selected)
                state =
                    state.copy(
                        adapter = adapter,
                        apiKey = if (adapter == ProviderAdapter.CODEX_CLI) "" else state.apiKey,
                        baseUrl = if (adapter == ProviderAdapter.CODEX_CLI) "" else state.baseUrl,
                    )
            },
        )
        if (state.adapter == ProviderAdapter.CODEX_CLI) {
            CodexCliProfileFields(
                state = state,
                accountStatus = accountStatus,
                versionStatus = versionStatus,
                pendingAction = pendingAccountAction,
                onStateChange = { state = it },
                onPendingActionChange = { pendingAccountAction = it },
                onRunAction = { action ->
                    pendingAccountAction = null
                    accountStatus = "Running Codex CLI $action..."
                    scope.launch {
                        val result =
                            when (action) {
                                "login" -> requireNotNull(codexCliAccountService).login(state.codexExecutablePath())
                                "device login" ->
                                    requireNotNull(codexCliAccountService).deviceLogin(state.codexExecutablePath()) { output ->
                                        withContext(Dispatchers.Main) { accountStatus = output }
                                    }
                                else -> requireNotNull(codexCliAccountService).logout(state.codexExecutablePath())
                            }
                        accountStatus = result.message
                    }
                },
            )
        } else {
            StandardProviderConnectionFields(state, apiKeyVisible, { state = it }, { apiKeyVisible = !apiKeyVisible })
        }
        PanelCheckbox(
            label = "Enabled",
            checked = state.enabled,
            enabled = canDisable,
            onCheckedChange = { state = state.copy(enabled = it) },
        )
        if (state.adapter != ProviderAdapter.CODEX_CLI) {
            OutlinedTextField(
                value = state.optionsText,
                onValueChange = { state = state.copy(optionsText = it) },
                label = { Text("Provider options") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (validation != null) Text(validation, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            ActionIconButton(icon = Icons.Filled.Close, description = "Cancel", onClick = onCancel)
            ActionIconButton(
                icon = Icons.Filled.Check,
                description = saveDescription,
                enabled = validation == null,
                onClick = { onSave(state.toProviderProfile(existing)) },
            )
        }
    }
}

@Composable
private fun StandardProviderConnectionFields(
    state: ProviderProfileFormState,
    apiKeyVisible: Boolean,
    onStateChange: (ProviderProfileFormState) -> Unit,
    onToggleApiKey: () -> Unit,
) {
    OutlinedTextField(
        value = state.baseUrl,
        onValueChange = { onStateChange(state.copy(baseUrl = it)) },
        label = { Text("Base URL") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.apiKey,
        onValueChange = { onStateChange(state.copy(apiKey = it)) },
        label = { Text("API key") },
        singleLine = true,
        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            ActionIconButton(
                icon = if (apiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                description = if (apiKeyVisible) "Hide API key" else "Show API key",
                onClick = onToggleApiKey,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CodexCliProfileFields(
    state: ProviderProfileFormState,
    accountStatus: String,
    versionStatus: String,
    pendingAction: String?,
    onStateChange: (ProviderProfileFormState) -> Unit,
    onPendingActionChange: (String?) -> Unit,
    onRunAction: (String) -> Unit,
) {
    OutlinedTextField(
        value = state.codexExecutablePath().orEmpty(),
        onValueChange = { value -> onStateChange(state.withCodexExecutablePath(value)) },
        label = { Text("Codex CLI path (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(versionStatus, style = MaterialTheme.typography.bodySmall)
    Text(accountStatus, style = MaterialTheme.typography.bodySmall)
    if (pendingAction == null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("login", "device login", "logout").forEach { action ->
                Button(onClick = { onPendingActionChange(action) }) { Text(action.replaceFirstChar(Char::uppercase)) }
            }
        }
    } else {
        Text("Confirm Codex CLI $pendingAction?", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onPendingActionChange(null) }) { Text("Cancel") }
            Button(onClick = { onRunAction(pendingAction) }) { Text("Confirm") }
        }
    }
}

private fun ProviderProfileFormState.codexExecutablePath(): String? =
    optionsText.toSettingsMap()[CodexCliProvider.OPTION_EXECUTABLE_PATH]?.takeIf(String::isNotBlank)

private fun ProviderProfileFormState.withCodexExecutablePath(value: String): ProviderProfileFormState {
    val options = optionsText.toSettingsMap().toMutableMap()
    if (value.isBlank()) {
        options.remove(CodexCliProvider.OPTION_EXECUTABLE_PATH)
    } else {
        options[CodexCliProvider.OPTION_EXECUTABLE_PATH] = value.trim()
    }
    return copy(optionsText = options.toSettingsMapText(), apiKey = "", baseUrl = "")
}

private fun de.heckenmann.visualagent.agent.codex.CodexLoginStatus.displayText(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

private fun CodexCliVersionInfo.displayText(): String {
    val statusText =
        when (status) {
            CodexCliUpdateStatus.UP_TO_DATE -> "Up to date"
            CodexCliUpdateStatus.UPDATE_AVAILABLE -> "Update available"
            CodexCliUpdateStatus.LATEST_UNAVAILABLE -> "Latest version unavailable"
            CodexCliUpdateStatus.CLI_NOT_FOUND -> "Codex CLI not found"
        }
    return "Installed: ${installedVersion ?: "not found"}\nLatest: ${latestVersion ?: "unavailable"}\nStatus: $statusText"
}
