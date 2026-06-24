package de.heckenmann.visualagent.ui.panels.canvas

import kotlinx.serialization.Serializable

/**
 * Model-facing operations for inspecting and mutating the structured canvas.
 */
interface CanvasOperations {
    /**
     * Reads the current canvas state.
     *
     * @return Snapshot containing all known figures and view state
     */
    fun snapshot(): CanvasSnapshot

    /**
     * Removes all figures from the canvas.
     *
     * @return Snapshot after clearing
     */
    fun clear(): CanvasSnapshot

    /**
     * Adds editable text to the canvas.
     *
     * @return Snapshot after insertion
     */
    fun drawText(
        text: String,
        x: Double,
        y: Double,
        color: String,
    ): CanvasSnapshot

    /**
     * Adds an editable rectangle to the canvas.
     *
     * @return Snapshot after insertion
     */
    fun drawRect(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        fillColor: String,
        strokeColor: String?,
    ): CanvasSnapshot

    /**
     * Adds an editable line to the canvas.
     *
     * @return Snapshot after insertion
     */
    fun drawLine(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        color: String,
        width: Double,
    ): CanvasSnapshot

    /**
     * Adds an editable circle to the canvas.
     *
     * @return Snapshot after insertion
     */
    fun drawCircle(
        centerX: Double,
        centerY: Double,
        radius: Double,
        fillColor: String,
    ): CanvasSnapshot

    /**
     * Adds an editable image figure from a local file.
     *
     * @return Snapshot after insertion
     */
    fun insertImage(path: String): CanvasSnapshot

    /**
     * Renders the current canvas as immutable image bytes.
     *
     * @param format Image format, currently `png`
     * @return Rendered image snapshot
     */
    fun captureImage(format: String): CanvasImageSnapshot
}

/**
 * Serializable canvas state exposed to model tools.
 *
 * @property figureCount Number of figures on the active layer
 * @property zoomPercent Current zoom level as an integer percent
 * @property gridVisible Whether the editing grid is visible
 * @property figures Ordered figure summaries
 */
@Serializable
data class CanvasSnapshot(
    val figureCount: Int,
    val zoomPercent: Int,
    val gridVisible: Boolean,
    val figures: List<CanvasFigureSnapshot>,
)

/**
 * Serializable summary of one editable canvas figure.
 *
 * @property index Figure index in draw order
 * @property type Figure type such as text, rectangle, line, circle, image, or stroke
 * @property x Figure layout X coordinate
 * @property y Figure layout Y coordinate
 * @property width Figure layout width
 * @property height Figure layout height
 */
@Serializable
data class CanvasFigureSnapshot(
    val index: Int,
    val type: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
)

/**
 * Immutable rendered image snapshot of the current canvas.
 *
 * @property format Encoded image format such as `png`
 * @property mimeType MIME type for displaying the image
 * @property bytes Encoded image bytes
 * @property width Rendered image width in pixels
 * @property height Rendered image height in pixels
 */
data class CanvasImageSnapshot(
    val format: String,
    val mimeType: String,
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
)
