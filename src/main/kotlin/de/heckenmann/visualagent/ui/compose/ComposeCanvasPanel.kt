@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.canvas.CanvasOperations
import de.heckenmann.visualagent.canvas.CanvasSnapshot
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes

/**
 * Canvas panel for drawing figures, importing images, and saving captures.
 *
 * Use cases: UC-0000028, UC-0000029, UC-0000030, UC-0000031, UC-0000032,
 * UC-0000033, UC-0000066, UC-0000071.
 *
 * @param canvasOperations Canvas mutation and persistence operations
 * @param workspaceFileService Workspace file import and managed-file creation
 * @param modalRequester Modal requester used for destructive confirmations
 */
@Composable
internal fun CanvasPanel(
    canvasOperations: CanvasOperations,
    workspaceFileService: WorkspaceFileService,
    modalRequester: ComposeModalRequester,
) {
    var snapshot by remember { mutableStateOf(canvasOperations.snapshot()) }
    var status by remember { mutableStateOf("Figures: ${snapshot.figureCount}") }
    var documentName by remember { mutableStateOf("canvas.canvas") }
    var captureName by remember { mutableStateOf("canvas-capture.png") }
    var mode by remember { mutableStateOf(CanvasInteractionMode.Select) }
    val update: (CanvasSnapshot) -> Unit = { next ->
        snapshot = next
        status =
            if (next.selectedFigureIndices.isEmpty()) {
                "Figures: ${next.figureCount}"
            } else {
                "Figures: ${next.figureCount} · selected ${next.selectedFigureIndices.size}"
            }
    }
    val imagePicker =
        rememberFilePickerLauncher { selected: PlatformFile? ->
            selected?.file?.let { file ->
                runCatching {
                    val imported = workspaceFileService.importFile(file)
                    canvasOperations.insertImage(imported.relativePath)
                }.onSuccess { update(it) }
                    .onFailure {
                        val userError =
                            de.heckenmann.visualagent.error.ErrorMessageMapper
                                .map(it)
                        status = "${userError.summary}: ${userError.detail}"
                    }
            }
        }
    val imageBytesForPath: (String) -> ByteArray? = { path ->
        runCatching { workspaceFileService.resolveManagedPath(path).readBytes() }
            .recoverCatching { Path.of(path).takeIf { it.isRegularFile() }?.readBytes() ?: throw it }
            .getOrNull()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CanvasDrawingToolbar(
                canvasOperations = canvasOperations,
                modalRequester = modalRequester,
                snapshot = snapshot,
                mode = mode,
                onModeChange = { mode = it },
                onImportImage = { imagePicker.launch() },
                update = update,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CanvasPersistenceToolbar(
                canvasOperations = canvasOperations,
                workspaceFileService = workspaceFileService,
                documentName = documentName,
                onDocumentNameChange = { documentName = it },
                captureName = captureName,
                onCaptureNameChange = { captureName = it },
            ) { status = it }
        }
        CanvasSurface(
            snapshot = snapshot,
            canvasOperations = canvasOperations,
            mode = mode,
            imageBytesForPath = imageBytesForPath,
            onSnapshotChanged = update,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        PanelStatus(status)
    }
}

@Composable
private fun CanvasDrawingToolbar(
    canvasOperations: CanvasOperations,
    modalRequester: ComposeModalRequester,
    snapshot: CanvasSnapshot,
    mode: CanvasInteractionMode,
    onModeChange: (CanvasInteractionMode) -> Unit,
    onImportImage: () -> Unit,
    update: (CanvasSnapshot) -> Unit,
) {
    ActionIconButton(
        icon = Icons.Filled.SelectAll,
        description = "Select mode",
        selected = mode == CanvasInteractionMode.Select,
        onClick = { onModeChange(CanvasInteractionMode.Select) },
    )
    ActionIconButton(
        icon = Icons.Filled.Gesture,
        description = "Pen mode",
        selected = mode == CanvasInteractionMode.Pen,
        onClick = { onModeChange(CanvasInteractionMode.Pen) },
    )
    ActionIconButton(
        icon = Icons.Filled.AddBox,
        description = "Add rectangle",
        onClick = { update(canvasOperations.drawRect(40.0, 50.0, 120.0, 80.0, "#50FA7B", "#F8F8F2")) },
    )
    ActionIconButton(
        icon = Icons.Filled.RadioButtonUnchecked,
        description = "Add circle",
        onClick = { update(canvasOperations.drawCircle(180.0, 130.0, 48.0, "#FF79C6")) },
    )
    ActionIconButton(
        icon = Icons.Filled.TextFields,
        description = "Add text",
        onClick = { update(canvasOperations.drawText("Visual Agent", 70.0, 220.0, "#F8F8F2")) },
    )
    ActionIconButton(
        icon = Icons.Filled.Image,
        description = "Import image",
        onClick = onImportImage,
    )
    ActionIconButton(
        icon = Icons.Filled.ClearAll,
        description = "Clear canvas",
        onClick = {
            modalRequester.requestConfirmation(
                ComposeConfirmationModal(
                    title = "Clear canvas?",
                    message = "Remove all figures from the current editable canvas.",
                    confirmDescription = "Clear canvas",
                ) {
                    update(canvasOperations.clear())
                },
            )
        },
    )
    val selectedCount = snapshot.selectedFigureIndices.size
    ActionIconButton(
        icon = Icons.Filled.Delete,
        description = "Delete selected figures",
        enabled = selectedCount > 0,
        onClick = {
            if (selectedCount > 0) {
                update(canvasOperations.deleteSelectedFigures())
            }
        },
    )
}

@Composable
private fun RowScope.CanvasPersistenceToolbar(
    canvasOperations: CanvasOperations,
    workspaceFileService: WorkspaceFileService,
    documentName: String,
    onDocumentNameChange: (String) -> Unit,
    captureName: String,
    onCaptureNameChange: (String) -> Unit,
    setStatus: (String) -> Unit,
) {
    OutlinedTextField(
        value = documentName,
        onValueChange = onDocumentNameChange,
        label = { Text("Document") },
        singleLine = true,
        modifier = Modifier.weight(1f),
    )
    ActionIconButton(
        icon = Icons.Filled.Save,
        description = "Save canvas document",
        enabled = documentName.isNotBlank(),
        onClick = {
            runCatching { canvasOperations.saveDocument(documentName.trim()) }
                .onSuccess { setStatus("Saved ${it.relativePath}") }
                .onFailure {
                    val userError =
                        de.heckenmann.visualagent.error.ErrorMessageMapper
                            .map(it)
                    setStatus("${userError.summary}: ${userError.detail}")
                }
        },
    )
    OutlinedTextField(
        value = captureName,
        onValueChange = onCaptureNameChange,
        label = { Text("PNG") },
        singleLine = true,
        modifier = Modifier.weight(1f),
    )
    ActionIconButton(
        icon = Icons.Filled.CameraAlt,
        description = "Capture canvas",
        enabled = captureName.isNotBlank(),
        onClick = {
            runCatching {
                val image = canvasOperations.captureImage("png")
                workspaceFileService.createManagedFile("canvas", captureName.trim(), image.bytes, image.mimeType)
            }.onSuccess {
                setStatus("Saved ${it.relativePath}")
            }.onFailure {
                val userError =
                    de.heckenmann.visualagent.error.ErrorMessageMapper
                        .map(it)
                setStatus("${userError.summary}: ${userError.detail}")
            }
        },
    )
}
