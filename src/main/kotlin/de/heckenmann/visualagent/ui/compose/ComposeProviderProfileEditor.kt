@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderProfile

@Composable
internal fun ProviderProfileEditor(
    initial: ProviderProfileFormState,
    existing: ProviderProfile?,
    canDisable: Boolean,
    onCancel: () -> Unit,
    onSave: (ProviderProfile) -> Unit,
) {
    var state by remember { mutableStateOf(initial) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    val validation = state.validationError()
    Column(
        modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.id,
                onValueChange = { state = state.copy(id = it) },
                label = { Text("Provider ID") },
                enabled = existing == null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.name,
                onValueChange = { state = state.copy(name = it) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        PanelDropdownField(
            label = "Adapter",
            selectedValue = state.adapter.name,
            options = ProviderAdapter.entries.map { PanelSelectOption(it.name, it.name) },
            onSelected = { state = state.copy(adapter = ProviderAdapter.valueOf(it)) },
        )
        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = { state = state.copy(baseUrl = it) },
            label = { Text("Base URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.apiKey,
            onValueChange = { state = state.copy(apiKey = it) },
            label = { Text("API key") },
            singleLine = true,
            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                ActionIconButton(
                    icon = if (apiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    description = if (apiKeyVisible) "Hide API key" else "Show API key",
                    onClick = { apiKeyVisible = !apiKeyVisible },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.defaultModel,
                onValueChange = { state = state.copy(defaultModel = it) },
                label = { Text("Default model") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            PanelCheckbox(
                label = "Enabled",
                checked = state.enabled,
                enabled = canDisable,
                onCheckedChange = { state = state.copy(enabled = it) },
            )
        }
        OutlinedTextField(
            value = state.optionsText,
            onValueChange = { state = state.copy(optionsText = it) },
            label = { Text("Provider options") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.modelsText,
            onValueChange = { state = state.copy(modelsText = it) },
            label = { Text("Models") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.whitelistText,
                onValueChange = { state = state.copy(whitelistText = it) },
                label = { Text("Model whitelist") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.blacklistText,
                onValueChange = { state = state.copy(blacklistText = it) },
                label = { Text("Model blacklist") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        if (validation != null) {
            Text(validation, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            ActionIconButton(icon = Icons.Filled.Close, description = "Cancel", onClick = onCancel)
            ActionIconButton(
                icon = Icons.Filled.Check,
                description = "Save provider",
                enabled = validation == null,
                onClick = { onSave(state.toProviderProfile(existing)) },
            )
        }
    }
}
