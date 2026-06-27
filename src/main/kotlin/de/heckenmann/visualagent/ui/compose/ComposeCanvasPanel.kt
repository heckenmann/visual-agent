@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.canvas.CanvasFigureSnapshot
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
private fun CanvasSurface(
    snapshot: CanvasSnapshot,
    canvasOperations: CanvasOperations,
    onSnapshotChanged: (CanvasSnapshot) -> Unit,
    modifier: Modifier,
) {
    val currentSnapshot by rememberUpdatedState(snapshot)
    val currentUpdate by rememberUpdatedState(onSnapshotChanged)
    val focusRequester = remember { FocusRequester() }
    Canvas(
        modifier =
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF191A21))
                .border(1.dp, Color(0x33444A65), RoundedCornerShape(14.dp))
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Delete || event.key == Key.Backspace)) {
                        currentSnapshot.selectedFigureIndex?.let { selected ->
                            currentUpdate(canvasOperations.deleteFigure(selected))
                            true
                        } ?: false
                    } else {
                        false
                    }
                }.focusable()
                .pointerInput(canvasOperations) {
                    var activeIndex: Int? = null
                    var resizeMode = false
                    var resizeWidth = 0.0
                    var resizeHeight = 0.0
                    detectDragGestures(
                        onDragStart = { offset ->
                            focusRequester.requestFocus()
                            val hit = currentSnapshot.hitTest(offset.x.toDouble(), offset.y.toDouble())
                            if (hit == null) {
                                activeIndex = null
                                currentUpdate(canvasOperations.selectFigure(null))
                            } else {
                                activeIndex = hit.index
                                resizeMode = hit.isResizeHandle(offset)
                                resizeWidth = hit.width
                                resizeHeight = hit.height
                                currentUpdate(canvasOperations.selectFigure(hit.index))
                            }
                        },
                    ) { _, dragAmount ->
                        val index = activeIndex ?: return@detectDragGestures
                        if (resizeMode) {
                            resizeWidth += dragAmount.x.toDouble()
                            resizeHeight += dragAmount.y.toDouble()
                            currentUpdate(canvasOperations.resizeFigure(index, resizeWidth, resizeHeight))
                        } else {
                            currentUpdate(canvasOperations.moveFigure(index, dragAmount.x.toDouble(), dragAmount.y.toDouble()))
                        }
                    }
                },
    ) {
        snapshot.figures.forEach { figure ->
            drawCanvasFigure(figure)
            if (figure.index == snapshot.selectedFigureIndex) drawSelection(figure)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCanvasFigure(figure: CanvasFigureSnapshot) {
    when (figure.type) {
        "circle" ->
            drawOval(
                color = Color(0xFFFF79C6),
                topLeft = Offset(figure.x.toFloat(), figure.y.toFloat()),
                size =
                    androidx.compose.ui.geometry
                        .Size(figure.width.toFloat(), figure.height.toFloat()),
            )
        "line" ->
            drawLine(
                color = Color(0xFF8BE9FD),
                start = Offset(figure.x.toFloat(), figure.y.toFloat()),
                end = Offset((figure.x + figure.width).toFloat(), (figure.y + figure.height).toFloat()),
                strokeWidth = 3f,
            )
        else ->
            drawRect(
                color = if (figure.type == "text") Color(0xFFFFB86C) else Color(0xFF50FA7B),
                topLeft = Offset(figure.x.toFloat(), figure.y.toFloat()),
                size =
                    androidx.compose.ui.geometry
                        .Size(figure.width.toFloat(), figure.height.toFloat()),
                style = if (figure.type == "text") Stroke(width = 2f) else Fill,
            )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSelection(figure: CanvasFigureSnapshot) {
    drawRect(
        color = Color(0xFFF8F8F2),
        topLeft = Offset(figure.x.toFloat(), figure.y.toFloat()),
        size =
            androidx.compose.ui.geometry
                .Size(figure.width.toFloat(), figure.height.toFloat()),
        style = Stroke(width = 2f),
    )
    drawRect(
        color = Color(0xFFFF79C6),
        topLeft = Offset((figure.x + figure.width - 8.0).toFloat(), (figure.y + figure.height - 8.0).toFloat()),
        size =
            androidx.compose.ui.geometry
                .Size(8f, 8f),
    )
}

private fun CanvasSnapshot.hitTest(
    x: Double,
    y: Double,
): CanvasFigureSnapshot? =
    figures
        .asReversed()
        .firstOrNull { figure ->
            x >= figure.x &&
                y >= figure.y &&
                x <= figure.x + figure.width &&
                y <= figure.y + figure.height
        }

private fun CanvasFigureSnapshot.isResizeHandle(offset: Offset): Boolean =
    offset.x >= (x + width - 18.0).toFloat() &&
        offset.y >= (y + height - 18.0).toFloat() &&
        offset.x <= (x + width + 8.0).toFloat() &&
        offset.y <= (y + height + 8.0).toFloat()
