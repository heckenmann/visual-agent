@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.canvas.CanvasFigureSnapshot
import de.heckenmann.visualagent.canvas.CanvasOperations
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
    onSnapshotChanged: (CanvasSnapshot) -> Unit,
    modifier: Modifier,
) {
    val canvasState = rememberInfiniteCanvasState()
    val nodeStates = remember { mutableStateMapOf<Int, CanvasNodeState>() }
    syncNodeStates(snapshot, nodeStates)

    LaunchedEffect(canvasState, snapshot.selectedFigureIndex) {
        snapshotFlow { canvasState.selectedNodeIds }
            .collectLatest { selectedIds ->
                val selectedIndex = selectedIds.firstOrNull()?.removePrefix("figure-")?.toIntOrNull()
                if (selectedIndex != snapshot.selectedFigureIndex) {
                    onSnapshotChanged(canvasOperations.selectFigure(selectedIndex))
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

    InfiniteCanvas(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
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
        nodes = canvasNodes(snapshot, nodeStates, canvasOperations, onSnapshotChanged),
    )
}

private fun canvasNodes(
    snapshot: CanvasSnapshot,
    nodeStates: SnapshotStateMap<Int, CanvasNodeState>,
    canvasOperations: CanvasOperations,
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
                    selected = figure.index == snapshot.selectedFigureIndex,
                    onResize = { width, height ->
                        onSnapshotChanged(canvasOperations.resizeFigure(figure.index, width, height))
                    },
                )
            },
        )
    }

@Composable
private fun FigureNode(
    figure: CanvasFigureSnapshot,
    selected: Boolean,
    onResize: (Double, Double) -> Unit,
) {
    val shape: Shape = if (figure.type == "circle") CircleShape else RoundedCornerShape(8.dp)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(figureColor(figure))
                .border(2.dp, if (selected) Color(0xFFF8F8F2) else Color.Transparent, shape)
                .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (figure.type == "text") {
            Text(
                text = "Text",
                color = Color(0xFF191A21),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            FigureResizeHandle(
                initialWidth = figure.width,
                initialHeight = figure.height,
                onResize = onResize,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

@Composable
private fun FigureResizeHandle(
    initialWidth: Double,
    initialHeight: Double,
    onResize: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var width by remember(initialWidth) { mutableStateOf(initialWidth) }
    var height by remember(initialHeight) { mutableStateOf(initialHeight) }
    Box(
        modifier =
            modifier
                .size(12.dp)
                .background(Color(0xFFFF79C6), RoundedCornerShape(4.dp))
                .pointerInput(initialWidth, initialHeight) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        width += dragAmount.x.toDouble()
                        height += dragAmount.y.toDouble()
                        onResize(width, height)
                    }
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
        if (figure.index != snapshot.selectedFigureIndex) {
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

private fun figureColor(figure: CanvasFigureSnapshot): Color =
    when (figure.type) {
        "circle" -> Color(0xFFFF79C6)
        "line" -> Color(0xFF8BE9FD)
        "text" -> Color(0xFFFFB86C)
        else -> Color(0xFF50FA7B)
    }
