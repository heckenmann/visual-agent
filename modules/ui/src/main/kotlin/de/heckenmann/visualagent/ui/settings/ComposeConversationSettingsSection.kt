package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.ui.components.ActionIconButton
import de.heckenmann.visualagent.ui.components.ActionTooltip
import de.heckenmann.visualagent.ui.components.PanelDropdownField
import de.heckenmann.visualagent.ui.components.PanelSection

/** Keeps only message-composition behavior in the contextual Conversation settings. */
@Composable
internal fun conversationSettingsSection(
    settings: SettingsSnapshot,
    onChange: (SettingsSnapshot) -> Unit,
    providers: List<ProviderProfile> = emptyList(),
) {
    PanelSection(title = "Conversation") {
        promptInterpolationSettings(settings, providers, onChange)
        conversationSettingField("Follow-up suggestions", "Suggestions are never submitted automatically.") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(settings.followUpSuggestionsEnabled, { onChange(settings.copy(followUpSuggestionsEnabled = it)) })
                Text("Suggest questions after an idle response")
            }
        }
        if (settings.followUpSuggestionsEnabled) {
            conversationNumberSetting(
                "Suggestion idle delay (seconds)",
                settings.followUpSuggestionIdleDelaySeconds,
                "Delay after a completed answer before requesting suggestions.",
                1..60,
                true,
            ) {
                onChange(settings.copy(followUpSuggestionIdleDelaySeconds = it))
            }
            conversationNumberSetting(
                "Suggestion count",
                settings.followUpSuggestionCount,
                "Number of questions requested per idle response.",
                1..5,
                true,
            ) {
                onChange(settings.copy(followUpSuggestionCount = it))
            }
        }
        conversationSettingField("Queue flush", "Send queued messages separately or together as a follow-up.") {
            PanelDropdownField(
                "",
                settings.queueFlushMode,
                queueFlushOptions(),
                onSelected = { onChange(settings.copy(queueFlushMode = it)) },
            )
        }
    }
}

/** Renders the configured context budget beside its authoritative model capacity. */
@Composable
internal fun modelContextLengthSetting(
    value: Int,
    modelLimit: Int?,
    onChange: (Int) -> Unit,
) {
    val maximum = modelLimit?.takeIf { it > 0 } ?: maxOf(32768, value)
    val minimum = minOf(1024, maximum)
    conversationSettingField("Context length", "Input and output share the selected model's context capacity.") {
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { text -> text.toIntOrNull()?.let { onChange(it.coerceIn(minimum, maximum)) } },
            label = { Text("Tokens") },
            supportingText = { Text(modelLimit?.takeIf { it > 0 }?.let { "Model limit: $it tokens" } ?: "Model limit not reported") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Context length" },
        )
    }
}

/** Shared labelled numeric field for the separated Agent and Conversation forms. */
@Composable
internal fun conversationNumberSetting(
    label: String,
    value: Int,
    help: String,
    range: IntRange,
    useStepper: Boolean = false,
    onChange: (Int) -> Unit,
) {
    conversationSettingField(label, help) {
        if (useStepper) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionIconButton(Icons.Filled.Remove, "Decrease $label", enabled = value > range.first, onClick = { onChange(value - 1) })
                Text(value.toString(), modifier = Modifier.semantics { contentDescription = label })
                ActionIconButton(Icons.Filled.Add, "Increase $label", enabled = value < range.last, onClick = { onChange(value + 1) })
            }
        } else {
            OutlinedTextField(
                value = value.toString(),
                onValueChange = { next -> next.toIntOrNull()?.let { onChange(it.coerceIn(range)) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
        }
    }
}

/** Shows contextual help on demand instead of a permanent explanatory block. */
@Composable
internal fun conversationSettingField(
    label: String,
    help: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label)
            ActionTooltip(description = help) {
                Icon(Icons.Filled.Info, contentDescription = "$label information", modifier = Modifier.size(16.dp))
            }
        }
        content()
    }
}
