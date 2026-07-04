@file:Suppress("FunctionName", "ktlint:standard:import-ordering")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.canvas.CanvasFigureSnapshot
import de.heckenmann.visualagent.canvas.CanvasOperations
import de.heckenmann.visualagent.canvas.CanvasPoint
import de.heckenmann.visualagent.canvas.CanvasSnapshot
import io.github.xingray.compose.infinitecanvas.CanvasNode
import io.github.xingray.compose.infinitecanvas.CanvasNodeState
import io.github.xingray.compose.infinitecanvas.InfiniteCanvas
import io.github.xingray.compose.infinitecanvas.InfiniteCanvasConfig
import io.github.xingray.compose.infinitecanvas.rememberInfiniteCanvasState
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

@Composable
internal fun CanvasSurface(
    snapshot: CanvasSnapshot,
    canvasOperations: CanvasOperations,
    mode: CanvasInteractionMode,
    imageBytesForPath: (String) -> ByteArray?,
    onSnapshotChanged: (CanvasSnapshot) -> Unit,
    modifier: Modifier,
) {
    val canvasState = rememberInfiniteCanvasState()
    val nodeStates = remember { mutableStateMapOf<Int, CanvasNodeState>() }
    var strokePoints by remember { mutableStateOf(emptyList<CanvasPoint>()) }
    val focusRequester = remember { FocusRequester() }
    syncNodeStates(snapshot, nodeStates)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(canvasState, snapshot.selectedFigureIndices) {
        snapshotFlow { canvasState.selectedNodeIds }
            .collectLatest { selectedIds ->
                val selectedIndices =
                    selectedIds
                        .mapNotNull { it.removePrefix("figure-").toIntOrNull() }
                        .filter { it in snapshot.figures.indices }
                        .toSortedSet()
                if (selectedIndices != snapshot.selectedFigureIndices) {
                    onSnapshotChanged(canvasOperations.selectFigures(selectedIndices))
                }
            }
    }
    LaunchedEffect(nodeStates, snapshot.figures) {
        snapshotFlow { nodePositions(snapshot.figures, nodeStates) }
            .collectLatest { positions ->
                val moved = positions.firstOrNull { position -> position.isMovedFrom(snapshot.figures) } ?: return@collectLatest
                val figure = snapshot.figures.first { it.index == moved.index }
                onSnapshotChanged(canvasOperations.moveFigure(figure.index, moved.x - figure.x, moved.y - figure.y))
            }
    }

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .focusTarget()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Delete || event.key == Key.Backspace) {
                        val selected = snapshot.selectedFigureIndices
                        if (selected.isNotEmpty()) {
                            onSnapshotChanged(canvasOperations.deleteSelectedFigures())
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                },
    ) {
        InfiniteCanvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .border(1.dp, Color(0x33444A65), RoundedCornerShape(8.dp)),
            state = canvasState,
            config =
                InfiniteCanvasConfig(
                    showGrid = true,
                    gridSize = 48f,
                    gridColor = Color(0x33444A65),
                    backgroundColor = Color(0xFF191A21),
                    showBottomControls = true,
                ),
            nodes = canvasNodes(snapshot, nodeStates, canvasOperations, imageBytesForPath, onSnapshotChanged),
        )
        if (mode == CanvasInteractionMode.Pen) {
            Canvas(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val worldPos = canvasState.viewport.screenToWorld(offset)
                                    strokePoints = listOf(CanvasPoint(worldPos.x.toDouble(), worldPos.y.toDouble()))
                                },
                                onDragEnd = {
                                    val points = strokePoints
                                    strokePoints = emptyList()
                                    if (points.size >= 2) {
                                        onSnapshotChanged(canvasOperations.drawStroke(points, "#F8F8F2", 3.0))
                                    }
                                },
                                onDragCancel = { strokePoints = emptyList() },
                            ) { change, _ ->
                                change.consume()
                                val worldPos = canvasState.viewport.screenToWorld(change.position)
                                strokePoints = strokePoints + CanvasPoint(worldPos.x.toDouble(), worldPos.y.toDouble())
                            }
                        },
            ) {
                strokePoints.zipWithNext().forEach { (start, end) ->
                    val startScreen =
                        canvasState.viewport.worldToScreen(
                            androidx.compose.ui.geometry.Offset(
                                start.x.toFloat(),
                                start.y.toFloat(),
                            ),
                        )
                    val endScreen =
                        canvasState.viewport.worldToScreen(
                            androidx.compose.ui.geometry.Offset(
                                end.x.toFloat(),
                                end.y.toFloat(),
                            ),
                        )
                    drawLine(
                        color = Color(0xFFF8F8F2),
                        start = startScreen,
                        end = endScreen,
                        strokeWidth = 3f,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

private fun canvasNodes(
    snapshot: CanvasSnapshot,
    nodeStates: SnapshotStateMap<Int, CanvasNodeState>,
    canvasOperations: CanvasOperations,
    imageBytesForPath: (String) -> ByteArray?,
    onSnapshotChanged: (CanvasSnapshot) -> Unit,
): List<CanvasNode> =
    snapshot.figures.map { figure ->
        CanvasNode(
            id = "figure-${figure.index}",
            state = nodeStates.getValue(figure.index),
            modifier =
                Modifier
                    .width(figure.width.dp)
                    .height(figure.height.dp),
            content = {
                FigureNode(
                    figure = figure,
                    selected = figure.index in snapshot.selectedFigureIndices,
                    imageBytesForPath = imageBytesForPath,
                    onResize = { width, height ->
                        onSnapshotChanged(canvasOperations.resizeFigure(figure.index, width, height))
                    },
                )
            },
        )
    }

private data class CanvasNodePosition(
    val index: Int,
    val x: Double,
    val y: Double,
)

private fun syncNodeStates(
    snapshot: CanvasSnapshot,
    nodeStates: SnapshotStateMap<Int, CanvasNodeState>,
) {
    val figureIndexes = snapshot.figures.map(CanvasFigureSnapshot::index).toSet()
    nodeStates.keys.removeAll { it !in figureIndexes }
    snapshot.figures.forEach { figure ->
        val nodeState = nodeStates.getOrPut(figure.index) { CanvasNodeState(figure.x.toFloat(), figure.y.toFloat()) }
        if (figure.index !in snapshot.selectedFigureIndices) {
            nodeState.x = figure.x.toFloat()
            nodeState.y = figure.y.toFloat()
        }
    }
}

private fun nodePositions(
    figures: List<CanvasFigureSnapshot>,
    nodeStates: SnapshotStateMap<Int, CanvasNodeState>,
): List<CanvasNodePosition> =
    figures.mapNotNull { figure ->
        nodeStates[figure.index]?.let { state ->
            CanvasNodePosition(figure.index, state.x.toDouble(), state.y.toDouble())
        }
    }

private fun CanvasNodePosition.isMovedFrom(figures: List<CanvasFigureSnapshot>): Boolean {
    val figure = figures.firstOrNull { it.index == index } ?: return false
    return abs(x - figure.x) > 0.5 || abs(y - figure.y) > 0.5
}

internal enum class CanvasInteractionMode {
    Select,
    Pen,
}
