@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.settings

import androidx.compose.runtime.Composable
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.protocol.ThemeMode
import de.heckenmann.visualagent.ui.components.NumericPanelField
import de.heckenmann.visualagent.ui.components.PanelCheckbox
import de.heckenmann.visualagent.ui.components.PanelDropdownField
import de.heckenmann.visualagent.ui.components.PanelSection
import de.heckenmann.visualagent.ui.components.PanelSelectOption

/** Renders persisted appearance settings and reports edits to the parent panel. */
@Composable
internal fun AppearanceSettingsSection(
    snapshot: SettingsSnapshot,
    onChange: (SettingsSnapshot) -> Unit,
) {
    PanelSection(title = "Appearance") {
        NumericPanelField(
            label = "Font size",
            value = snapshot.fontSize.toString(),
            onValueChange = { value ->
                onChange(snapshot.copy(fontSize = value.toIntOrNull()?.coerceIn(10, 24) ?: snapshot.fontSize))
            },
            information = "Sets the base text size throughout the application.",
        )
        PanelDropdownField(
            label = "UI scale",
            selectedValue = snapshot.uiScalePercent?.let { "$it%" } ?: "Automatic",
            options = uiScaleOptions(),
            onSelected = { selected ->
                onChange(snapshot.copy(uiScalePercent = selected.removeSuffix("%").toIntOrNull()))
            },
            information = "Scales the interface. Automatic uses the operating system scale.",
        )
        PanelDropdownField(
            label = "Theme",
            selectedValue = snapshot.uiThemeMode.name,
            options = ThemeMode.entries.map { PanelSelectOption(it.name, it.name.lowercase().replaceFirstChar(Char::titlecase)) },
            onSelected = { selected -> onChange(snapshot.copy(uiThemeMode = ThemeMode.valueOf(selected))) },
            information = "Selects light, dark, or the operating system's preferred appearance.",
        )
        PanelCheckbox(
            label = "Show panel labels",
            checked = snapshot.showPanelLabels,
            onCheckedChange = { onChange(snapshot.copy(showPanelLabels = it)) },
            information = "Shows or hides labels in the workspace navigation rail.",
        )
    }
}

private fun uiScaleOptions(): List<PanelSelectOption> =
    listOf(PanelSelectOption("Automatic", "Automatic")) +
        listOf(75, 80, 90, 100, 110, 125, 150, 175, 200).map { percent ->
            PanelSelectOption("$percent%", "$percent%")
        }
