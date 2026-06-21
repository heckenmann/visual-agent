package de.heckenmann.visualagent.ui.panels.canvas

import javafx.geometry.Point2D
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.StackPane
import javafx.scene.shape.Polyline
import org.jhotdraw8.draw.SimpleDrawingView

/**
 * Handles freehand pen pointer events for the editable canvas.
 *
 * Use cases: UC-0000028.
 */
internal class CanvasPenInputController(
    private val editorViewport: StackPane,
    private val drawingView: SimpleDrawingView,
    private val strokePreview: Polyline,
    private val activeTool: () -> CanvasTool,
    private val addStroke: (List<Point2D>) -> Unit,
) {
    private var pendingStroke: MutableList<Point2D>? = null

    /** Installs mouse filters that collect and commit pen strokes. */
    fun install() {
        editorViewport.addEventFilter(MouseEvent.MOUSE_PRESSED) { event ->
            if (activeTool() != CanvasTool.PEN || event.button != MouseButton.PRIMARY) return@addEventFilter
            pendingStroke = mutableListOf(worldPoint(event))
            strokePreview.points.setAll(event.x, event.y)
            event.consume()
        }
        editorViewport.addEventFilter(MouseEvent.MOUSE_DRAGGED) { event ->
            val points = pendingStroke ?: return@addEventFilter
            points += worldPoint(event)
            strokePreview.points.addAll(event.x, event.y)
            event.consume()
        }
        editorViewport.addEventFilter(MouseEvent.MOUSE_RELEASED) { event ->
            if (event.button != MouseButton.PRIMARY || pendingStroke == null) return@addEventFilter
            commitPendingStroke()
            event.consume()
        }
    }

    private fun commitPendingStroke() {
        val points = pendingStroke.orEmpty()
        pendingStroke = null
        strokePreview.points.clear()
        if (points.size >= 2) addStroke(points)
    }

    private fun worldPoint(event: MouseEvent): Point2D {
        val viewPoint = drawingView.node.sceneToLocal(event.sceneX, event.sceneY)
        return drawingView.viewToWorld(viewPoint)
    }
}
