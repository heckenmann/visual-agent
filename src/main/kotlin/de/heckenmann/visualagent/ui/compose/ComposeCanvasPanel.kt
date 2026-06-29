@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
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

@Composable
internal fun CanvasPanel(
    canvasOperations: CanvasOperations,
    workspaceFileService: WorkspaceFileService,
    modalRequester: ComposeModalRequester,
) {
    var snapshot by remember { mutableStateOf(canvasOperations.snapshot()) }
    var status by remember { mutableStateOf("Figures: ${snapshot.figureCount}") }
    val update: (CanvasSnapshot) -> Unit = { next ->
        snapshot = next
        status =
            if (next.selectedFigureIndex == null) {
                "Figures: ${next.figureCount}"
            } else {
                "Figures: ${next.figureCount} · selected ${next.selectedFigureIndex}"
            }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CanvasToolbar(canvasOperations, workspaceFileService, modalRequester, snapshot, update) { status = it }
        }
        CanvasSurface(
            snapshot = snapshot,
            canvasOperations = canvasOperations,
            onSnapshotChanged = update,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        PanelStatus(status)
    }
}

@Composable
private fun CanvasToolbar(
    canvasOperations: CanvasOperations,
    workspaceFileService: WorkspaceFileService,
    modalRequester: ComposeModalRequester,
    snapshot: CanvasSnapshot,
    update: (CanvasSnapshot) -> Unit,
    setStatus: (String) -> Unit,
) {
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
    ActionIconButton(
        icon = Icons.Filled.Delete,
        description = "Delete selected figure",
        enabled = snapshot.selectedFigureIndex != null,
        onClick = {
            snapshot.selectedFigureIndex?.let { selected ->
                modalRequester.requestConfirmation(
                    ComposeConfirmationModal(
                        title = "Delete selected figure?",
                        message = "Remove figure $selected from the current editable canvas.",
                        confirmDescription = "Delete selected figure",
                    ) {
                        update(canvasOperations.deleteFigure(selected))
                    },
                )
            }
        },
    )
    ActionIconButton(
        icon = Icons.Filled.CameraAlt,
        description = "Capture canvas",
        onClick = {
            runCatching {
                val image = canvasOperations.captureImage("png")
                workspaceFileService.createManagedFile("canvas", "canvas-capture.png", image.bytes, image.mimeType)
            }.onSuccess {
                setStatus("Saved ${it.relativePath}")
            }.onFailure {
                setStatus("Capture failed: ${it.message}")
            }
        },
    )
    ActionIconButton(
        icon = Icons.Filled.Save,
        description = "Save canvas document",
        onClick = {
            runCatching { canvasOperations.saveDocument("canvas.canvas") }
                .onSuccess { setStatus("Saved ${it.relativePath}") }
                .onFailure { setStatus("Save failed: ${it.message}") }
        },
    )
}
