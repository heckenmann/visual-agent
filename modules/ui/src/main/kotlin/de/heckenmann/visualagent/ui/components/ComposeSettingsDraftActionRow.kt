package de.heckenmann.visualagent.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
        OutlinedButton(onClick = onReset, enabled = hasUnsavedChanges && !saving) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Text("Reset changes")
        }
        Button(onClick = onSave, enabled = hasUnsavedChanges && !saving) {
            Icon(Icons.Filled.Save, contentDescription = null)
            Text(if (saving) "Saving..." else "Save changes")
        }
    }
}
