package de.heckenmann.visualagent.ui.skills

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.heckenmann.visualagent.protocol.SkillDocument
import de.heckenmann.visualagent.ui.components.ComposeMarkdown
import de.heckenmann.visualagent.ui.modal.modalDialogLayout
import de.heckenmann.visualagent.ui.modal.modalPrimaryButton
import de.heckenmann.visualagent.ui.modal.modalSecondaryButton

/** Renders a read-only skill document with copy, edit, and delete actions. */
@Composable
internal fun SkillDetail(
    document: SkillDocument,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    modalDialogLayout(
        body = {
            Text(
                "Revision ${document.summary.revision} · ${document.summary.readCount} model reads" +
                    (document.summary.lastReadAt?.let { " · last read $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectionContainer {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ComposeMarkdown(document.content)
                }
            }
        },
        footer = {
            modalSecondaryButton("Copy Markdown", icon = Icons.Filled.ContentCopy, onClick = onCopy)
            modalSecondaryButton("Edit skill", icon = Icons.Filled.Edit, onClick = onEdit)
            modalSecondaryButton("Delete skill", icon = Icons.Filled.Delete, onClick = onDelete)
            modalPrimaryButton("Close", icon = Icons.Filled.Close, onClick = onDismiss)
        },
    )
}
