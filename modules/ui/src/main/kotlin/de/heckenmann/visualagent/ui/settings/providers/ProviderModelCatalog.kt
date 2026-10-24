package de.heckenmann.visualagent.ui.settings.providers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.heckenmann.visualagent.protocol.CapabilitySupport
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.capabilitySupport
import de.heckenmann.visualagent.ui.components.ActionIconButton
import de.heckenmann.visualagent.ui.components.PanelDropdownField
import de.heckenmann.visualagent.ui.components.PanelInfoBox
import de.heckenmann.visualagent.ui.components.PanelSelectOption
import de.heckenmann.visualagent.ui.settings.LegacyCapabilityReset
import de.heckenmann.visualagent.ui.settings.ModelCapabilitiesEditor
import de.heckenmann.visualagent.ui.settings.ProviderSettingsDraft
import de.heckenmann.visualagent.ui.settings.resetLegacyCapabilities
import de.heckenmann.visualagent.ui.settings.toSettingsMap
import de.heckenmann.visualagent.ui.settings.toSettingsMapText
import de.heckenmann.visualagent.ui.settings.withModelCapabilities

/** Compact provider directory; selecting an entry never changes the main-agent model. */
@Composable
internal fun ProviderDirectory(
    profiles: List<ProviderProfile>,
    selected: String,
    onSelected: (String) -> Unit,
    compact: Boolean = true,
    enabled: Boolean = true,
) {
    if (compact) {
        PanelDropdownField(
            "Provider",
            selected,
            profiles.map { PanelSelectOption(it.id, it.name) },
            enabled = enabled,
            onSelected = onSelected,
        )
    } else {
        LazyColumn {
            items(profiles, key = ProviderProfile::id) { profile ->
                ListItem(
                    headlineContent = { Text(profile.name) },
                    supportingContent = { Text(if (profile.enabled) profile.adapter.name.replace('_', ' ') else "Disabled") },
                    colors =
                        ListItemDefaults.colors(
                            containerColor =
                                if (profile.id == selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                profile.id == selected,
                                enabled = enabled,
                                onClick = { onSelected(profile.id) },
                            ).semantics { contentDescription = "Configure provider: ${profile.id}" },
                )
            }
        }
    }
}

/** Keeps model metadata and advanced configuration in provider administration, not in the composer. */
@Composable
internal fun ProviderModelCatalog(
    draft: ProviderSettingsDraft,
    busy: Boolean,
    onChange: (ProviderSettingsDraft) -> Unit,
    onRefresh: () -> Unit,
    state: ProviderManagementState = remember { ProviderManagementState() },
) {
    val profile = draft.providers.firstOrNull { it.id == draft.providerId } ?: return
    val model = profile.models.firstOrNull { it.id == draft.modelId }
    val optionsKey = "${profile.id}/${model?.id}"
    val options = state.modelOptions[optionsKey] ?: model?.options?.toSettingsMapText().orEmpty()
    var advanced by remember(profile.id, model?.id) { mutableStateOf(false) }
    PanelDropdownField(
        "Model catalog",
        draft.modelId,
        profile.models.map { PanelSelectOption(it.id, it.name) },
        enabled = profile.models.isNotEmpty() && !busy,
        onSelected = { onChange(draft.copy(modelId = it)) },
    )
    ActionIconButton(Icons.Filled.Refresh, "Refresh models", enabled = !busy, onClick = onRefresh)
    if (model?.capabilitySupport("chat") == CapabilitySupport.UNSUPPORTED) {
        PanelInfoBox(
            "This model's current capability declaration excludes chat. Review its advanced capability settings; the model remains visible in this catalog.",
        )
    }
    LegacyCapabilityReset(draft) { onChange(draft.resetLegacyCapabilities(profile.id)) }
    ActionIconButton(Icons.Filled.Tune, "Advanced model configuration", selected = advanced, onClick = { advanced = !advanced })
    if (advanced && model != null) {
        Column {
            ModelCapabilitiesEditor(model) { capability, supported ->
                onChange(draft.withModelCapabilities(profile.id, model.id, capability, supported))
            }
            OutlinedTextField(
                value = options,
                enabled = !busy,
                onValueChange = { text ->
                    state.modelOptions[optionsKey] = text
                    state.optionsDirty = true
                    if (!validModelOptions(text)) return@OutlinedTextField
                    onChange(
                        draft.copy(
                            providers =
                                draft.providers.map { current ->
                                    if (current.id != profile.id) {
                                        current
                                    } else {
                                        current.copy(
                                            models =
                                                current.models.map {
                                                    if (it.id == model.id) it.copy(options = text.toSettingsMap()) else it
                                                },
                                        )
                                    }
                                },
                        ),
                    )
                },
                label = { Text("Model options (key=value per line)") },
                isError = !validModelOptions(options),
                supportingText = {
                    Text(
                        if (validModelOptions(options)) {
                            "Only options supported by the selected provider are accepted."
                        } else {
                            "Use unique, nonblank keys and one key=value pair per line."
                        },
                    )
                },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Rejects incomplete or duplicate options rather than silently discarding unsaved text. */
internal fun validModelOptions(text: String): Boolean {
    val lines = text.lines().filter(String::isNotBlank)
    return lines.all { '=' in it && it.substringBefore('=').isNotBlank() } &&
        lines.map { it.substringBefore('=').trim() }.distinct().size == lines.size
}
