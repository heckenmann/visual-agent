@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.canvas.CanvasOperations
import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.workspace.WorkspaceFilePaths
import de.heckenmann.visualagent.workspace.WorkspaceFileService

/**
 * Renders a single workspace file row with rename, copy, open-canvas, and delete actions.
 */
@Composable
internal fun WorkspaceFileRow(
    file: WorkspaceFileRecord,
    workspaceFileService: WorkspaceFileService,
    canvasOperations: CanvasOperations,
    modalRequester: ComposeModalRequester,
    refresh: () -> Unit,
    setStatus: (String) -> Unit,
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    PanelContentCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
    ) {
        Text(file.relativePath, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "${file.mimeType} · ${file.sizeBytes} bytes · ${file.sha256.take(12)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionIconButton(
                icon = Icons.Filled.Edit,
                description = "Rename workspace file",
                onClick = {
                    modalRequester.request(
                        ComposeContentModal(title = "Rename file") { dismiss ->
                            RenameFileDialog(
                                currentName = file.relativePath.substringAfterLast('/'),
                                onCancel = dismiss,
                                onRename = { name ->
                                    runCatching { workspaceFileService.renameFile(file.id, name) }
                                        .onSuccess {
                                            setStatus("Renamed to ${it.relativePath}")
                                            refresh()
                                            dismiss()
                                        }.onFailure {
                                            val userError =
                                                de.heckenmann.visualagent.error.ErrorMessageMapper
                                                    .map(it)
                                            setStatus("${userError.summary}: ${userError.detail}")
                                        }
                                },
                            )
                        },
                    )
                },
            )
            ActionIconButton(
                icon = Icons.Filled.ContentCopy,
                description = "Copy file metadata",
                onClick = {
                    clipboard.setText(AnnotatedString(file.toClipboardMetadata()))
                    setStatus("Copied metadata for ${file.relativePath}")
                },
            )
            if (file.mimeType == WorkspaceFilePaths.CANVAS_MIME_TYPE) {
                ActionIconButton(
                    icon = Icons.Filled.FileOpen,
                    description = "Open canvas document",
                    onClick = {
                        runCatching { canvasOperations.openDocument(file.id, null) }
                            .onSuccess { setStatus("Opened ${file.relativePath}") }
                            .onFailure {
                                val userError =
                                    de.heckenmann.visualagent.error.ErrorMessageMapper
                                        .map(it)
                                setStatus("${userError.summary}: ${userError.detail}")
                            }
                    },
                )
            }
            ActionIconButton(
                icon = Icons.Filled.Delete,
                description = "Delete workspace file",
                onClick = {
                    modalRequester.requestConfirmation(
                        ComposeConfirmationModal(
                            title = "Delete workspace file?",
                            message = "Delete '${file.relativePath}' from the managed workspace and metadata store.",
                            confirmDescription = "Delete workspace file",
                        ) {
                            workspaceFileService.deleteFile(file.id)
                            setStatus("Deleted ${file.relativePath}")
                            refresh()
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun RenameFileDialog(
    currentName: String,
    onCancel: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember(currentName) { mutableStateOf(currentName) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("File name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            ActionIconButton(icon = Icons.Filled.Close, description = "Cancel rename", onClick = onCancel)
            ActionIconButton(
                icon = Icons.Filled.Done,
                description = "Rename workspace file",
                enabled = name.isNotBlank(),
                onClick = { onRename(name.trim()) },
            )
        }
    }
}

private fun WorkspaceFileRecord.toClipboardMetadata(): String =
    buildString {
        appendLine("path=$relativePath")
        appendLine("mimeType=$mimeType")
        appendLine("sizeBytes=$sizeBytes")
        appendLine("sha256=$sha256")
    }.trimEnd()
