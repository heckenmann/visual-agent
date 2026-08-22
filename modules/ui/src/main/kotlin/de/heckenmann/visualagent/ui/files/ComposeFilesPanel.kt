@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.CANVAS_MIME_TYPE
import de.heckenmann.visualagent.protocol.CanvasPort
import de.heckenmann.visualagent.protocol.MAX_WORKSPACE_FILE_IMPORT_BYTES
import de.heckenmann.visualagent.protocol.WorkspaceDownload
import de.heckenmann.visualagent.protocol.WorkspaceFile
import de.heckenmann.visualagent.protocol.WorkspaceFilePort
import de.heckenmann.visualagent.ui.agents.*
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.canvas.*
import de.heckenmann.visualagent.ui.components.*
import de.heckenmann.visualagent.ui.conversation.*
import de.heckenmann.visualagent.ui.files.*
import de.heckenmann.visualagent.ui.modal.*
import de.heckenmann.visualagent.ui.settings.*
import de.heckenmann.visualagent.ui.status.*
import de.heckenmann.visualagent.ui.todo.*
import de.heckenmann.visualagent.ui.workspace.*
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    workspaceFileService: WorkspaceFilePort,
    canvasOperations: CanvasPort,
    modalRequester: ComposeModalRequester,
    activityPort: ActivityPort,
) {
    var files by remember { mutableStateOf(emptyList<WorkspaceFile>()) }
    var directories by remember { mutableStateOf(emptyList<String>()) }
    var currentDirectory by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf(ALL_FILE_TYPES) }
    var status by remember { mutableStateOf("Loading workspace…") }
    var downloads by remember { mutableStateOf(emptyList<WorkspaceDownload>()) }
    val scope = rememberCoroutineScope()

    /** Refreshes workspace metadata and active download state off the UI thread. */
    suspend fun refreshWorkspace() {
        val snapshot =
            withContext(Dispatchers.IO) {
                WorkspaceBrowserSnapshot(
                    workspaceFileService.listFiles(),
                    workspaceFileService.listDirectories(),
                    workspaceFileService.activeDownloads(),
                    workspaceFileService.workspaceRoot(),
                )
            }
        files = snapshot.files
        directories = snapshot.directories
        downloads = snapshot.downloads
        if (status == "Loading workspace…") status = "Workspace: ${snapshot.root}"
    }
    val refresh: () -> Unit = {
        scope.launch { refreshWorkspace() }
    }
    LaunchedEffect(workspaceFileService) { refreshWorkspace() }
    DisposableEffect(workspaceFileService) {
        val downloadHandle =
            workspaceFileService.addDownloadListener {
                refresh()
            }
        val activityHandle =
            workspaceFileService.addListener {
                refresh()
            }
        onDispose {
            downloadHandle.close()
            activityHandle.close()
        }
    }
    ToolEventRefreshEffect(
        activityPort = activityPort,
        toolIds = setOf("file:write", "file:edit", "workspace:file", "javascript:execute"),
        onRefresh = refresh,
    )
    val listing = browseWorkspaceFiles(files, currentDirectory, directories)
    val visibleFiles = filterWorkspaceFiles(listing.files, query, typeFilter)
    val fileListScrollState = rememberScrollState()
    RegisterPanelVerticalScrollbar(fileListScrollState)

    /** Validates and imports one file asynchronously without allocating oversized input on Main. */
    fun importFile(file: File) {
        when {
            !file.isFile -> status = "Operation failed: File does not exist"
            file.length() > MAX_WORKSPACE_FILE_IMPORT_BYTES ->
                status = "Operation failed: File is larger than 50 MB"
            else -> {
                val targetDirectory = currentDirectory
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            workspaceFileService.importFile(targetDirectory, file.name, file.readBytes())
                        }
                    }.onSuccess {
                        status = "Imported ${it.relativePath}"
                        refresh()
                    }.onFailure {
                        status = it.toUiErrorMessage()
                    }
                }
            }
        }
    }
    val picker =
        rememberFilePickerLauncher { selected: PlatformFile? ->
            selected?.file?.let(::importFile)
        }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionIconButton(
                icon = Icons.Filled.FolderOpen,
                description = "Import file into current folder",
                onClick = { picker.launch() },
            )
            ActionIconButton(
                icon = Icons.Filled.CreateNewFolder,
                description = "Create folder in current folder",
                onClick = {
                    modalRequester.request(
                        ComposeContentModal(title = "Create folder") { dismiss ->
                            CreateWorkspaceFolderDialog(
                                onCancel = dismiss,
                                onCreate = { name ->
                                    scope.launch {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                workspaceFileService.createDirectory(currentDirectory, name)
                                            }
                                        }.onSuccess {
                                            status = "Created folder $it"
                                            directories = (directories + it).distinct().sorted()
                                            dismiss()
                                        }.onFailure { status = it.toUiErrorMessage() }
                                    }
                                },
                            )
                        },
                    )
                },
            )
            ActionIconButton(
                icon = Icons.Filled.ArrowUpward,
                description = "Open parent folder",
                enabled = currentDirectory.isNotBlank(),
                onClick = {
                    currentDirectory = currentDirectory.substringBeforeLast('/', "")
                },
            )
            ActionIconButton(
                icon = Icons.Filled.Refresh,
                description = "Sync workspace files",
                onClick = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { workspaceFileService.syncMetadataWithFilesystem() }
                        status = "Sync added=${result.added} updated=${result.updated} removed=${result.removed}"
                        refreshWorkspace()
                    }
                },
            )
        }
        WorkspaceBreadcrumbs(currentDirectory) { currentDirectory = it }
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
            text = "Folder ${currentDirectory.ifBlank { "/" }} · ${files.size} total · ${visibleFiles.size} visible",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f).verticalScroll(fileListScrollState)) {
            val currentDownloads = downloads.filter { it.relativePath.substringBeforeLast('/', "") == currentDirectory }
            if (listing.directories.isEmpty() && visibleFiles.isEmpty() && currentDownloads.isEmpty()) {
                PanelEmptyState(
                    title = "No matching files",
                    body = "Import a file, sync the workspace directory, or change the current filter.",
                )
            } else {
                listing.directories.forEach { directory ->
                    WorkspaceDirectoryRow(
                        directory = directory,
                        activeDownloads = downloads.count { it.relativePath.substringBeforeLast('/', "") == directory.relativePath },
                        onOpen = { currentDirectory = directory.relativePath },
                    )
                }
                currentDownloads.forEach { download ->
                    WorkspaceDownloadRow(
                        download = download,
                        onPause = { workspaceFileService.pauseDownload(download.id) },
                        onResume = { workspaceFileService.resumeDownload(download.id) },
                        onCancel = { workspaceFileService.cancelDownload(download.id) },
                    )
                }
                visibleFiles.forEach { file ->
                    WorkspaceFileRow(
                        file = file,
                        workspaceFileService = workspaceFileService,
                        canvasOperations = canvasOperations,
                        modalRequester = modalRequester,
                        refresh = refresh,
                        setStatus = { status = it },
                        locked = downloads.any { it.relativePath == file.relativePath },
                    )
                }
            }
        }
        PanelStatus(status)
    }
}

/** Snapshot used to refresh the browser from the server-owned workspace. */
private data class WorkspaceBrowserSnapshot(
    val files: List<WorkspaceFile>,
    val directories: List<String>,
    val downloads: List<WorkspaceDownload>,
    val root: String,
)

/**
 * Filters workspace records by free-text query and type category.
 *
 * @param files All workspace records
 * @param query Free-text query matched against path, original name, and SHA-256
 * @param typeFilter One of [ALL_FILE_TYPES], [CANVAS_FILE_TYPE], or [OTHER_FILE_TYPE]
 * @return Filtered list in the original order
 */
internal fun filterWorkspaceFiles(
    files: List<WorkspaceFile>,
    query: String,
    typeFilter: String,
): List<WorkspaceFile> =
    files.filter { file ->
        val matchesQuery =
            query.isBlank() ||
                file.relativePath.contains(query, ignoreCase = true) ||
                file.originalName.contains(query, ignoreCase = true) ||
                file.sha256.contains(query, ignoreCase = true)
        val matchesType =
            typeFilter == ALL_FILE_TYPES ||
                (typeFilter == CANVAS_FILE_TYPE && file.mimeType == CANVAS_MIME_TYPE) ||
                (typeFilter == OTHER_FILE_TYPE && file.mimeType != CANVAS_MIME_TYPE)
        matchesQuery && matchesType
    }

internal const val ALL_FILE_TYPES = "__all__"
internal const val CANVAS_FILE_TYPE = "canvas"
internal const val OTHER_FILE_TYPE = "other"
