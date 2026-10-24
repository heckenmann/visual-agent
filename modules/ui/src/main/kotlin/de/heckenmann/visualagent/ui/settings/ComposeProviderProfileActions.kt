package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.ui.components.ActionIconButton

/** Displays provider profile editing actions for a staged settings draft. */
@Composable
internal fun providerProfileActions(
    canEdit: Boolean,
    canRemove: Boolean,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    canAdd: Boolean = true,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        ActionIconButton(Icons.Filled.Add, "Add provider", enabled = canAdd, onClick = onAdd)
        ActionIconButton(Icons.Filled.Edit, "Edit provider", enabled = canEdit, onClick = onEdit)
        ActionIconButton(Icons.Filled.Delete, "Remove provider", enabled = canRemove, onClick = onRemove)
    }
}
