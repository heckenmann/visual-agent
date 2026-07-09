@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
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
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import java.io.File

/**
 * Workspace files panel for importing, searching, syncing, and deleting files.
 *
 * Use cases: UC-0000023, UC-0000024, UC-0000025, UC-0000026, UC-0000031,
 * UC-0000071.
 *
 * @param workspaceFileService Workspace file import, sync, and metadata
 * @param canvasOperations Canvas document opener for `.canvas` files
 * @param modalRequester Modal requester used for destructive confirmations
 */
@Composable
internal fun FilesPanel(
    workspaceFileService: WorkspaceFileService,
    canvasOperations: CanvasOperations,
    modalRequester: ComposeModalRequester,
) {
    var files by remember { mutableStateOf(workspaceFileService.listFiles()) }
    var path by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf(ALL_FILE_TYPES) }
    var status by remember { mutableStateOf("Workspace: ${workspaceFileService.workspaceRoot()}") }
    val refresh = {
        files = workspaceFileService.listFiles()
    }
    val visibleFiles = filterWorkspaceFiles(files, query, typeFilter)
    val importFile: (File) -> Unit = { file ->
        runCatching { workspaceFileService.importFile(file) }
            .onSuccess {
                status = "Imported ${it.relativePath}"
                path = ""
                refresh()
            }.onFailure {
                val userError =
                    de.heckenmann.visualagent.error.ErrorMessageMapper
                        .map(it)
                status = "${userError.summary}: ${userError.detail}"
            }
    }
    val picker =
        rememberFilePickerLauncher { selected: PlatformFile? ->
            selected?.file?.let(importFile)
        }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                label = { Text("Import path") },
                trailingIcon = {
                    ActionIconButton(
                        icon = Icons.Filled.Add,
                        description = "Import typed path",
                        onClick = {
                            val typedPath = path.trim()
                            if (typedPath.isNotBlank()) importFile(File(typedPath))
                        },
                        enabled = path.isNotBlank(),
                    )
                },
                modifier = Modifier.weight(1f),
            )
            ActionIconButton(
                icon = Icons.Filled.FolderOpen,
                description = "Open file picker",
                onClick = { picker.launch() },
            )
            ActionIconButton(
                icon = Icons.Filled.Refresh,
                description = "Sync workspace files",
                onClick = {
                    val result = workspaceFileService.syncMetadataWithFilesystem()
                    status = "Sync added=${result.added} updated=${result.updated} removed=${result.removed}"
                    refresh()
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search files") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            PanelDropdownField(
                label = "Type",
                selectedValue = typeFilter,
                options =
                    listOf(
                        PanelSelectOption(ALL_FILE_TYPES, "All files"),
                        PanelSelectOption(CANVAS_FILE_TYPE, "Canvas"),
                        PanelSelectOption(OTHER_FILE_TYPE, "Other"),
                    ),
                onSelected = { typeFilter = it },
                modifier = Modifier.weight(0.45f),
            )
        }
        Text(
            text = "Total ${files.size} · showing ${visibleFiles.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (visibleFiles.isEmpty()) {
                PanelEmptyState(
                    title = "No matching files",
                    body = "Import a file, sync the workspace directory, or change the current filter.",
                )
            } else {
                visibleFiles.forEach { file ->
                    WorkspaceFileRow(file, workspaceFileService, canvasOperations, modalRequester, refresh) { status = it }
                }
            }
        }
        PanelStatus(status)
    }
}

@Composable
private fun WorkspaceFileRow(
    file: WorkspaceFileRecord,
    workspaceFileService: WorkspaceFileService,
    canvasOperations: CanvasOperations,
    modalRequester: ComposeModalRequester,
    refresh: () -> Unit,
    setStatus: (String) -> Unit,
) {
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

/**
 * Filters workspace records by free-text query and type category.
 *
 * @param files All workspace records
 * @param query Free-text query matched against path, original name, and SHA-256
 * @param typeFilter One of [ALL_FILE_TYPES], [CANVAS_FILE_TYPE], or [OTHER_FILE_TYPE]
 * @return Filtered list in the original order
 */
internal fun filterWorkspaceFiles(
    files: List<WorkspaceFileRecord>,
    query: String,
    typeFilter: String,
): List<WorkspaceFileRecord> =
    files.filter { file ->
        val matchesQuery =
            query.isBlank() ||
                file.relativePath.contains(query, ignoreCase = true) ||
                file.originalName.contains(query, ignoreCase = true) ||
                file.sha256.contains(query, ignoreCase = true)
        val matchesType =
            typeFilter == ALL_FILE_TYPES ||
                (typeFilter == CANVAS_FILE_TYPE && file.mimeType == WorkspaceFilePaths.CANVAS_MIME_TYPE) ||
                (typeFilter == OTHER_FILE_TYPE && file.mimeType != WorkspaceFilePaths.CANVAS_MIME_TYPE)
        matchesQuery && matchesType
    }

internal const val ALL_FILE_TYPES = "__all__"
internal const val CANVAS_FILE_TYPE = "canvas"
internal const val OTHER_FILE_TYPE = "other"
