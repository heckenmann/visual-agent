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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.canvas.CanvasOperations
import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import de.heckenmann.visualagent.workspace.WorkspaceFilePaths
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import java.io.File

@Composable
internal fun FilesPanel(
    workspaceFileService: WorkspaceFileService,
    canvasOperations: CanvasOperations,
    modalRequester: ComposeModalRequester,
) {
    var files by remember { mutableStateOf(workspaceFileService.listFiles()) }
    var path by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Workspace: ${workspaceFileService.workspaceRoot()}") }
    val refresh = {
        files = workspaceFileService.listFiles()
    }
    val importFile: (File) -> Unit = { file ->
        runCatching { workspaceFileService.importFile(file) }
            .onSuccess {
                status = "Imported ${it.relativePath}"
                path = ""
                refresh()
            }.onFailure {
                status = "Import failed: ${it.message}"
            }
    }
    val picker =
        rememberFilePickerLauncher { selected: PlatformFile? ->
            selected?.file?.let(importFile)
        }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                label = { Text("Import path") },
                modifier = Modifier.weight(1f),
            )
            ActionIconButton(
                icon = Icons.Filled.FolderOpen,
                description = "Open file picker",
                onClick = { picker.launch() },
            )
            ActionIconButton(
                icon = Icons.Filled.Add,
                description = "Import typed path",
                onClick = {
                    val typedPath = path.trim()
                    if (typedPath.isNotBlank()) importFile(File(typedPath))
                },
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
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            files.forEach { file ->
                WorkspaceFileRow(file, workspaceFileService, canvasOperations, modalRequester, refresh) { status = it }
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
    var newName by remember(file.id) { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(file.relativePath, color = Color(0xFFF8F8F2), fontWeight = FontWeight.SemiBold)
        Text("${file.mimeType} · ${file.sizeBytes} bytes · ${file.sha256.take(12)}", color = Color(0xFFBD93F9))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Rename") },
                modifier = Modifier.weight(1f),
            )
            ActionIconButton(
                icon = Icons.Filled.Edit,
                description = "Rename workspace file",
                onClick = {
                    val name = newName.trim()
                    if (name.isNotBlank()) {
                        runCatching { workspaceFileService.renameFile(file.id, name) }
                            .onSuccess {
                                setStatus("Renamed to ${it.relativePath}")
                                newName = ""
                                refresh()
                            }.onFailure { setStatus("Rename failed: ${it.message}") }
                    }
                },
            )
            if (file.mimeType == WorkspaceFilePaths.CANVAS_MIME_TYPE) {
                ActionIconButton(
                    icon = Icons.Filled.FileOpen,
                    description = "Open canvas document",
                    onClick = {
                        runCatching { canvasOperations.openDocument(file.id, null) }
                            .onSuccess { setStatus("Opened ${file.relativePath}") }
                            .onFailure { setStatus("Open failed: ${it.message}") }
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
