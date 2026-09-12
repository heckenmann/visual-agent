package de.heckenmann.visualagent.ui.skills

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.heckenmann.visualagent.protocol.SkillSearchResult
import de.heckenmann.visualagent.ui.components.PanelInfoBox
import de.heckenmann.visualagent.ui.modal.modalDialogLayout
import de.heckenmann.visualagent.ui.modal.modalSaveButton
import de.heckenmann.visualagent.ui.modal.modalSecondaryButton

/** Renders the skill editor body and its modal footer actions. */
@Composable
internal fun SkillEditor(
    title: String,
    content: String,
    isNew: Boolean,
    conflict: SkillConflict?,
    saveError: String?,
    onTitleChanged: (String) -> Unit,
    onContentChanged: (String) -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit,
    onKeepDraft: () -> Unit,
    onSaveAsNew: () -> Unit,
    onDiscard: () -> Unit,
) {
    val titleLength = title.codePointCount(0, title.length)
    val contentLength = content.codePointCount(0, content.length)
    val titleInvalid = title.isBlank() || titleLength > MAX_TITLE_CODE_POINTS
    val contentInvalid = content.isBlank() || contentLength > MAX_CONTENT_CODE_POINTS
    modalDialogLayout(
        body = {
            saveError?.let { PanelInfoBox(it) }
            conflict?.let { state ->
                PanelInfoBox(
                    when (state) {
                        is SkillConflict.Changed ->
                            "This skill changed elsewhere (revision ${state.skill.revision}). " +
                                "Reload the stored version or keep this draft."
                        SkillConflict.Deleted -> "This skill was deleted elsewhere. Save this draft as a new skill or discard it."
                    },
                )
            }
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChanged,
                label = { Text("Title") },
                singleLine = true,
                isError = titleInvalid,
                supportingText = { Text("$titleLength/$MAX_TITLE_CODE_POINTS Unicode code points") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = content,
                onValueChange = onContentChanged,
                label = { Text("Markdown") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 12,
                isError = contentInvalid,
                supportingText = { Text("$contentLength/$MAX_CONTENT_CODE_POINTS Unicode code points") },
            )
        },
        footer = {
            when (val state = conflict) {
                is SkillConflict.Changed -> {
                    modalSecondaryButton("Reload stored version", icon = Icons.Filled.Restore, onClick = onReload)
                    modalSecondaryButton("Keep draft", icon = Icons.Filled.Edit, onClick = onKeepDraft)
                }
                SkillConflict.Deleted -> {
                    modalSecondaryButton("Save as new", icon = Icons.Filled.Save, onClick = onSaveAsNew)
                    modalSecondaryButton("Discard draft", icon = Icons.Filled.Close, onClick = onDiscard)
                }
                null -> Unit
            }
            modalSecondaryButton("Reset", icon = Icons.Filled.Restore, onClick = onReset)
            modalSecondaryButton("Cancel", onClick = onCancel)
            modalSaveButton(
                label = if (isNew) "Create skill" else "Save skill",
                enabled = !titleInvalid && !contentInvalid,
                onClick = onSave,
            )
        },
    )
}

/** Describes an optimistic-concurrency conflict in the editor. */
internal sealed interface SkillConflict {
    /** A newer revision was saved by another client. */
    data class Changed(
        val skill: SkillSearchResult,
    ) : SkillConflict

    /** The selected skill was deleted by another client. */
    data object Deleted : SkillConflict
}

internal const val MAX_TITLE_CODE_POINTS = 200
internal const val MAX_CONTENT_CODE_POINTS = 120_000
internal const val MAX_QUERY_CODE_POINTS = 500
