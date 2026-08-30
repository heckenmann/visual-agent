@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.files

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.heckenmann.visualagent.ui.modal.modalDialogLayout
import de.heckenmann.visualagent.ui.modal.modalPrimaryButton
import de.heckenmann.visualagent.ui.modal.modalSecondaryButton

/** Collects a name for a new managed workspace directory. */
@Composable
internal fun CreateWorkspaceFolderDialog(
    onCancel: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    modalDialogLayout(
        body = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Folder name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        footer = {
            modalSecondaryButton(label = "Cancel", onClick = onCancel)
            modalPrimaryButton(
                label = "Create folder",
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim()) },
            )
        },
    )
}
