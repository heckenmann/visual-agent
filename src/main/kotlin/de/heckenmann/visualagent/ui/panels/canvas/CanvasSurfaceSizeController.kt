package de.heckenmann.visualagent.ui.panels.canvas

import de.heckenmann.visualagent.knowledge.PreferenceStore
import javafx.application.Platform
import javafx.geometry.BoundingBox
import javafx.scene.control.TextInputDialog
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import org.jhotdraw8.css.value.CssSize
import org.jhotdraw8.draw.SimpleDrawingView
import org.jhotdraw8.draw.figure.Drawing
import org.jhotdraw8.draw.gui.ZoomableScrollPane

/**
 * Stores and applies the user-configured editable canvas surface size.
 *
 * Use cases: UC-0000029.
 */
internal class CanvasSurfaceSizeController(
    private val preferenceStore: PreferenceStore,
    private val editorViewport: StackPane,
    private val drawingView: SimpleDrawingView,
) {
    private var canvasWidth = loadCanvasDimension(CANVAS_WIDTH_KEY, DEFAULT_CANVAS_WIDTH)
    private var canvasHeight = loadCanvasDimension(CANVAS_HEIGHT_KEY, DEFAULT_CANVAS_HEIGHT)

    /** Sets and persists the editable canvas surface size. */
    fun setCanvasSize(
        width: Double,
        height: Double,
    ) {
        canvasWidth = width.coerceIn(MIN_CANVAS_SIZE, MAX_CANVAS_SIZE)
        canvasHeight = height.coerceIn(MIN_CANVAS_SIZE, MAX_CANVAS_SIZE)
        preferenceStore.setPreference(CANVAS_WIDTH_KEY, canvasWidth.toInt().toString())
        preferenceStore.setPreference(CANVAS_HEIGHT_KEY, canvasHeight.toInt().toString())
        applyCanvasSize()
    }

    /** Returns the configured editable canvas surface size. */
    fun canvasSize(): Pair<Double, Double> = canvasWidth to canvasHeight

    /** Applies the configured size to a drawing before it is attached to the view model. */
    fun applyDrawingSize(drawing: Drawing) {
        drawing.set(Drawing.WIDTH, CssSize.of(canvasWidth))
        drawing.set(Drawing.HEIGHT, CssSize.of(canvasHeight))
    }

    /** Prompts the user for a new editable canvas surface size. */
    fun promptCanvasSize() {
        val result =
            TextInputDialog("${canvasWidth.toInt()}x${canvasHeight.toInt()}")
                .apply {
                    title = "Canvas size"
                    headerText = "Set editable canvas size"
                    contentText = "Width x Height"
                }.showAndWait()
        if (result.isEmpty) return
        val parts =
            result
                .get()
                .lowercase()
                .split("x", ",", " ")
                .filter(String::isNotBlank)
        if (parts.size < 2) return
        setCanvasSize(parts[0].toDoubleOrNull() ?: canvasWidth, parts[1].toDoubleOrNull() ?: canvasHeight)
    }

    /** Applies the configured size to the viewport and JHotDraw surface node. */
    fun applyCanvasSize() {
        editorViewport.minWidth = 0.0
        editorViewport.minHeight = 0.0
        editorViewport.prefWidth = Region.USE_COMPUTED_SIZE
        editorViewport.prefHeight = Region.USE_COMPUTED_SIZE
        drawingView.drawing?.let(::applyDrawingSize)
        val scrollPane = jHotDrawScrollPane()
        scrollPane?.setContentSize(canvasWidth, canvasHeight)
        if (drawingView.node is Region) {
            val surface = drawingView.node as Region
            surface.minWidth = 0.0
            surface.minHeight = 0.0
            surface.prefWidth = Region.USE_COMPUTED_SIZE
            surface.prefHeight = Region.USE_COMPUTED_SIZE
            surface.maxWidth = Double.MAX_VALUE
            surface.maxHeight = Double.MAX_VALUE
        }
        jHotDrawCanvasRegion()?.resizeToCanvasSize()
        scrollCanvasOriginIntoView(scrollPane)
    }

    private fun scrollCanvasOriginIntoView(scrollPane: ZoomableScrollPane?) {
        val origin = BoundingBox(0.0, 0.0, 1.0, 1.0)
        scrollPane?.scrollContentRectToVisible(origin)
        drawingView.scrollRectToVisible(origin)
        Platform.runLater {
            scrollPane?.scrollContentRectToVisible(origin)
            drawingView.scrollRectToVisible(origin)
        }
    }

    private fun jHotDrawScrollPane(): ZoomableScrollPane? =
        runCatching {
            val field = SimpleDrawingView::class.java.getDeclaredField("zoomableScrollPane")
            field.isAccessible = true
            field.get(drawingView) as? ZoomableScrollPane
        }.getOrNull()

    private fun jHotDrawCanvasRegion(): Region? =
        runCatching {
            val field = SimpleDrawingView::class.java.getDeclaredField("background")
            field.isAccessible = true
            field.get(drawingView) as? Region
        }.getOrNull()

    private fun Region.resizeToCanvasSize() {
        minWidth = canvasWidth
        minHeight = canvasHeight
        prefWidth = canvasWidth
        prefHeight = canvasHeight
        maxWidth = canvasWidth
        maxHeight = canvasHeight
    }

    private fun loadCanvasDimension(
        key: String,
        fallback: Double,
    ): Double =
        preferenceStore
            .getPreference(key)
            ?.toDoubleOrNull()
            ?.coerceIn(MIN_CANVAS_SIZE, MAX_CANVAS_SIZE)
            ?: fallback

    private companion object {
        const val CANVAS_WIDTH_KEY = "canvas.surface.width"
        const val CANVAS_HEIGHT_KEY = "canvas.surface.height"
        const val DEFAULT_CANVAS_WIDTH = 1200.0
        const val DEFAULT_CANVAS_HEIGHT = 800.0
        const val MIN_CANVAS_SIZE = 100.0
        const val MAX_CANVAS_SIZE = 10_000.0
    }
}
