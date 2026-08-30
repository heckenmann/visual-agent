package de.heckenmann.visualagent.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Renders the shared secondary-reset and primary-save actions for a staged settings draft. */
@Composable
internal fun settingsDraftActionRow(
    hasUnsavedChanges: Boolean,
    saving: Boolean,
    onReset: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        resetActionButton(onClick = onReset, enabled = hasUnsavedChanges && !saving)
        saveActionButton(label = if (saving) "Saving..." else "Save changes", onClick = onSave, enabled = hasUnsavedChanges && !saving)
    }
}
