package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.ui.components.PanelDropdownField
import de.heckenmann.visualagent.ui.components.PanelSelectOption

/** Stages interpolation settings independently of the main-agent model. */
@Composable
internal fun promptInterpolationSettings(
    settings: SettingsSnapshot,
    providers: List<ProviderProfile>,
    onChange: (SettingsSnapshot) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = settings.automaticPromptInterpolation,
                modifier = Modifier.semantics { contentDescription = "Automatic prompt interpolation" },
                onCheckedChange = { onChange(settings.copy(automaticPromptInterpolation = it)) },
            )
            Text("Automatically refine prompts before sending")
        }
        Text("Manual refinement never sends a message. Automatic refinement uses an extra model request.")
        val enabled = providers.filter(ProviderProfile::enabled)
        val provider = enabled.firstOrNull { it.id == settings.promptInterpolationProviderId }
        PanelDropdownField(
            label = "Interpolation provider",
            selectedValue = settings.promptInterpolationProviderId,
            options = listOf(PanelSelectOption("", "Use active provider and model")) + enabled.map { PanelSelectOption(it.id, it.name) },
            onSelected = { id ->
                val selected = enabled.firstOrNull { it.id == id }
                val models = selected?.selectableModels().orEmpty()
                val model = models.firstOrNull { it.id == selected?.defaultModel } ?: models.firstOrNull()
                onChange(settings.copy(promptInterpolationProviderId = id, promptInterpolationModelId = model?.id.orEmpty()))
            },
        )
        if (settings.promptInterpolationProviderId.isNotBlank()) {
            PanelDropdownField(
                label = "Interpolation model",
                selectedValue = settings.promptInterpolationModelId,
                options = provider?.selectableModels().orEmpty().map { PanelSelectOption(it.id, it.name) },
                onSelected = { onChange(settings.copy(promptInterpolationModelId = it)) },
            )
        }
    }
}
