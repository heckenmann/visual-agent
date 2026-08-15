@file:Suppress("FunctionName")

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
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.ui.components.ActionIconButton
import de.heckenmann.visualagent.ui.components.PanelCheckbox
import de.heckenmann.visualagent.ui.components.PanelDropdownField
import de.heckenmann.visualagent.ui.components.PanelSelectOption
import java.util.UUID

/** Mutable form state for creating or editing a provider profile. */
internal data class ProviderProfileFormState(
    val id: String = "",
    val name: String = "",
    val adapter: ProviderAdapter = ProviderAdapter.OPENAI_COMPATIBLE,
    val baseUrl: String = "",
    val apiKey: String = "",
    val enabled: Boolean = true,
    val defaultModel: String = "",
    val optionsText: String = "",
)

/** Edits one provider profile without exposing application or Spring types to the UI module. */
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
        OutlinedTextField(
            value = state.id,
            onValueChange = { state = state.copy(id = it) },
            label = { Text("Provider ID") },
            singleLine = true,
            enabled = existing == null,
            modifier = Modifier.fillMaxWidth(),
        )
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
            options = ProviderAdapter.entries.map { PanelSelectOption(it.name, it.name.replace('_', ' ')) },
            onSelected = { selected ->
                val adapter = ProviderAdapter.valueOf(selected)
                state =
                    state.copy(
                        adapter = adapter,
                        apiKey =
                            if (adapter ==
                                ProviderAdapter.CODEX_CLI
                            ) {
                                ""
                            } else {
                                state.apiKey
                            },
                        baseUrl =
                            if (adapter ==
                                ProviderAdapter.CODEX_CLI
                            ) {
                                ""
                            } else {
                                state.baseUrl
                            },
                    )
            },
        )
        if (state.adapter != ProviderAdapter.CODEX_CLI) {
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
        }
        OutlinedTextField(
            value = state.defaultModel,
            onValueChange = { state = state.copy(defaultModel = it) },
            label = { Text("Default model") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.adapter != ProviderAdapter.CODEX_CLI) {
            OutlinedTextField(
                value = state.optionsText,
                onValueChange = { state = state.copy(optionsText = it) },
                label = { Text("Provider options (key=value per line)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        PanelCheckbox(
            label = "Enabled",
            checked = state.enabled,
            enabled = canDisable,
            onCheckedChange = { state = state.copy(enabled = it) },
        )
        validation?.let { Text(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            ActionIconButton(icon = Icons.Filled.Close, description = "Cancel", onClick = onCancel)
            ActionIconButton(
                icon = Icons.Filled.Check,
                description = if (existing == null) "Create provider" else "Save provider changes",
                enabled = validation == null,
                onClick = { onSave(state.toProviderProfile(existing)) },
            )
        }
    }
}

internal fun ProviderProfile.toFormState(): ProviderProfileFormState =
    ProviderProfileFormState(id, name, adapter, baseUrl, apiKey, enabled, defaultModel, options.toSettingsMapText())

internal fun newProviderFormState(): ProviderProfileFormState =
    ProviderProfileFormState(
        id = "provider-${UUID.randomUUID()}",
        name = "New provider",
        adapter = ProviderAdapter.OPENAI_COMPATIBLE,
        baseUrl = "https://api.example.com",
    )

private fun ProviderProfileFormState.validationError(): String? =
    when {
        id.isBlank() -> "Provider ID is required."
        !id.trim().matches(Regex("[a-zA-Z0-9._-]+")) -> "Provider ID contains invalid characters."
        name.isBlank() -> "Name is required."
        adapter != ProviderAdapter.CODEX_CLI && baseUrl.isBlank() -> "Base URL is required."
        else -> null
    }

private fun ProviderProfileFormState.toProviderProfile(existing: ProviderProfile?): ProviderProfile =
    ProviderProfile(
        id = existing?.id ?: id.trim(),
        name = name.trim(),
        adapter = adapter,
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        enabled = enabled,
        defaultModel = defaultModel.trim(),
        options = optionsText.toSettingsMap(),
        models = existing?.models.orEmpty(),
        modelWhitelist = existing?.modelWhitelist.orEmpty(),
        modelBlacklist = existing?.modelBlacklist.orEmpty(),
    )

internal fun Map<String, String>.toSettingsMapText(): String =
    entries.sortedBy { it.key }.joinToString("\n") { (key, value) -> "$key=$value" }

internal fun String.toSettingsMap(): Map<String, String> =
    lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filter { it.contains('=') }
        .associate { line -> line.substringBefore('=').trim() to line.substringAfter('=').trim() }
        .filterKeys(String::isNotBlank)
