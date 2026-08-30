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
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.ui.components.ActionIconButton
import de.heckenmann.visualagent.ui.components.ActionTooltip
import de.heckenmann.visualagent.ui.components.PanelDropdownField
import de.heckenmann.visualagent.ui.components.PanelSection

/** Renders the conversation settings that are staged with the selected provider and model. */
@Composable
internal fun conversationSettingsSection(
    settings: SettingsSnapshot,
    onChange: (SettingsSnapshot) -> Unit,
) {
    PanelSection(title = "Conversation") {
        conversationSettingField(
            label = "Model instruction",
            help = "Optional guidance added to every main-agent request. It can set tone, language, or lasting preferences.",
        ) {
            OutlinedTextField(
                value = settings.userModelInstruction,
                onValueChange = { onChange(settings.copy(userModelInstruction = it)) },
                placeholder = { Text("Optional instruction for the main agent") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Model instruction" },
            )
        }
        conversationNumberSetting(
            label = "Context length",
            value = settings.contextLength,
            help = "Maximum context budget sent to the model. Larger values preserve more history but can use more provider capacity.",
            range = 1024..32768,
            onChange = { value -> onChange(settings.copy(contextLength = value)) },
        )
        conversationNumberSetting(
            label = "Startup history",
            value = settings.loadLimit,
            help =
                "Number of persisted conversation messages loaded when the application starts. " +
                    "Higher values make more history immediately available.",
            range = 1..1000,
            onChange = { value -> onChange(settings.copy(loadLimit = value)) },
        )
        conversationNumberSetting(
            label = "Parallel agents",
            value = settings.maxParallelSubAgents,
            help =
                "Maximum number of sub-agents that may work simultaneously. Higher values can finish " +
                    "independent work sooner but consume more resources.",
            range = 1..20,
            useStepper = true,
            onChange = { value -> onChange(settings.copy(maxParallelSubAgents = value)) },
        )
        conversationNumberSetting(
            label = "Default tool timeout (seconds)",
            value = settings.timeoutSeconds,
            help =
                "Default maximum duration for each model-invoked tool call. The model may request a " +
                    "different timeout within the supported range.",
            range = 5..600,
            onChange = { value -> onChange(settings.copy(timeoutSeconds = value)) },
        )
        conversationSettingField(
            label = "Queue flush",
            help =
                "Controls how messages queued while the main agent is busy are delivered: separately or " +
                    "combined into one follow-up request.",
        ) {
            PanelDropdownField(
                label = "",
                selectedValue = settings.queueFlushMode,
                options = queueFlushOptions(),
                onSelected = { selected -> onChange(settings.copy(queueFlushMode = selected)) },
            )
        }
    }
}

@Composable
private fun conversationNumberSetting(
    label: String,
    value: Int,
    help: String,
    range: IntRange,
    useStepper: Boolean = false,
    onChange: (Int) -> Unit,
) {
    conversationSettingField(label, help) {
        if (useStepper) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionIconButton(
                    icon = Icons.Filled.Remove,
                    description = "Decrease $label",
                    tooltipDescription = "Decrease $label by one",
                    enabled = value > range.first,
                    onClick = { onChange(value - 1) },
                )
                Text(value.toString(), modifier = Modifier.semantics { contentDescription = label })
                ActionIconButton(
                    icon = Icons.Filled.Add,
                    description = "Increase $label",
                    tooltipDescription = "Increase $label by one",
                    enabled = value < range.last,
                    onClick = { onChange(value + 1) },
                )
            }
        } else {
            OutlinedTextField(
                value = value.toString(),
                onValueChange = { next -> next.toIntOrNull()?.let { parsed -> onChange(parsed.coerceIn(range)) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
        }
    }
}

@Composable
private fun conversationSettingField(
    label: String,
    help: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label)
            ActionTooltip(description = help) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "$label information",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        content()
    }
}
