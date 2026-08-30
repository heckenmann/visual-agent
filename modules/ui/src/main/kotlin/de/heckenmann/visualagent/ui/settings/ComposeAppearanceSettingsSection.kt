@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.protocol.ThemeMode
import de.heckenmann.visualagent.ui.components.NumericPanelField
import de.heckenmann.visualagent.ui.components.PanelCheckbox
import de.heckenmann.visualagent.ui.components.PanelDropdownField
import de.heckenmann.visualagent.ui.components.PanelSection
import de.heckenmann.visualagent.ui.components.PanelSelectOption

/** Renders persisted execution settings and reports edits to the parent panel. */
@Composable
internal fun RuntimeSettingsSection(
    snapshot: SettingsSnapshot,
    onChange: (SettingsSnapshot) -> Unit,
) {
    PanelSection(title = "Execution") {
        NumericPanelField(
            "Context length",
            snapshot.contextLength.toString(),
            onValueChange = { value ->
                onChange(snapshot.copy(contextLength = value.toIntOrNull()?.coerceIn(1024, 32768) ?: snapshot.contextLength))
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumericPanelField(
                "Startup history",
                snapshot.loadLimit.toString(),
                onValueChange = { value ->
                    onChange(snapshot.copy(loadLimit = value.toIntOrNull()?.coerceIn(1, 1000) ?: snapshot.loadLimit))
                },
                modifier = Modifier.weight(1f),
            )
            NumericPanelField(
                "Parallel agents",
                snapshot.maxParallelSubAgents.toString(),
                onValueChange = { value ->
                    onChange(snapshot.copy(maxParallelSubAgents = value.toIntOrNull()?.coerceIn(1, 20) ?: snapshot.maxParallelSubAgents))
                },
                modifier = Modifier.weight(1f),
            )
            NumericPanelField(
                "Timeout sec",
                snapshot.timeoutSeconds.toString(),
                onValueChange = { value ->
                    onChange(snapshot.copy(timeoutSeconds = value.toIntOrNull()?.coerceIn(5, 600) ?: snapshot.timeoutSeconds))
                },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelCheckbox("Stream", snapshot.streamingEnabled, onCheckedChange = { onChange(snapshot.copy(streamingEnabled = it)) })
            PanelCheckbox("Reasoning", snapshot.thinkingEnabled, onCheckedChange = { onChange(snapshot.copy(thinkingEnabled = it)) })
            PanelCheckbox(
                "Compaction",
                snapshot.autoCompactionEnabled,
                onCheckedChange = { onChange(snapshot.copy(autoCompactionEnabled = it)) },
            )
        }
        PanelDropdownField(
            "Queue flush",
            snapshot.queueFlushMode,
            queueFlushOptions(),
            onSelected = { selected -> onChange(snapshot.copy(queueFlushMode = selected)) },
        )
        OutlinedTextField(
            value = snapshot.userModelInstruction,
            onValueChange = { onChange(snapshot.copy(userModelInstruction = it)) },
            label = { Text("Model instruction") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Renders persisted appearance settings and reports edits to the parent panel. */
@Composable
internal fun AppearanceSettingsSection(
    snapshot: SettingsSnapshot,
    onChange: (SettingsSnapshot) -> Unit,
) {
    PanelSection(title = "Appearance") {
        NumericPanelField(
            "Font size",
            snapshot.fontSize.toString(),
            onValueChange = { value ->
                onChange(snapshot.copy(fontSize = value.toIntOrNull()?.coerceIn(10, 24) ?: snapshot.fontSize))
            },
        )
        PanelDropdownField(
            "UI scale",
            snapshot.uiScalePercent?.let { "$it%" } ?: "Automatic",
            uiScaleOptions(),
            onSelected = { selected ->
                onChange(snapshot.copy(uiScalePercent = selected.removeSuffix("%").toIntOrNull()))
            },
        )
        PanelDropdownField(
            "Theme",
            snapshot.uiThemeMode.name,
            ThemeMode.entries.map { PanelSelectOption(it.name, it.name.lowercase().replaceFirstChar(Char::titlecase)) },
            onSelected = { selected -> onChange(snapshot.copy(uiThemeMode = ThemeMode.valueOf(selected))) },
        )
        PanelCheckbox("Show panel labels", snapshot.showPanelLabels, onCheckedChange = { onChange(snapshot.copy(showPanelLabels = it)) })
    }
}

private fun uiScaleOptions(): List<PanelSelectOption> =
    listOf(PanelSelectOption("Automatic", "Automatic")) +
        listOf(75, 80, 90, 100, 110, 125, 150, 175, 200).map { percent ->
            PanelSelectOption("$percent%", "$percent%")
        }
