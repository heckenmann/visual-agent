package de.heckenmann.visualagent.ui.panels.canvas

import de.heckenmann.visualagent.knowledge.PreferenceStore
import javafx.scene.control.TextInputDialog
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import org.jhotdraw8.draw.SimpleDrawingView

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
        editorViewport.minWidth = canvasWidth
        editorViewport.minHeight = canvasHeight
        editorViewport.prefWidth = canvasWidth
        editorViewport.prefHeight = canvasHeight
        if (drawingView.node is Region) {
            val surface = drawingView.node as Region
            surface.minWidth = canvasWidth
            surface.minHeight = canvasHeight
            surface.prefWidth = canvasWidth
            surface.prefHeight = canvasHeight
        }
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
