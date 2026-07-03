package de.heckenmann.visualagent.canvas

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
     * Adds an editable freehand stroke to the canvas.
     *
     * @return Snapshot after insertion
     */
    fun drawStroke(
        points: List<CanvasPoint>,
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
     * Replaces the current selection with a set of figure indices.
     *
     * @param indices Figure indices to select; an empty set clears the selection
     * @return Snapshot after selection
     */
    fun selectFigures(indices: Set<Int>): CanvasSnapshot

    /**
     * Selects the top-most figure at a canvas coordinate, replacing any existing selection.
     *
     * @param x Canvas X coordinate
     * @param y Canvas Y coordinate
     * @return Snapshot after hit-testing
     */
    fun selectAt(
        x: Double,
        y: Double,
    ): CanvasSnapshot

    /**
     * Moves one figure by a delta and selects it.
     *
     * @param index Figure index
     * @param deltaX Horizontal movement
     * @param deltaY Vertical movement
     * @return Snapshot after moving
     */
    fun moveFigure(
        index: Int,
        deltaX: Double,
        deltaY: Double,
    ): CanvasSnapshot

    /**
     * Resizes one figure while keeping its top-left coordinate stable and selects it.
     *
     * @param index Figure index
     * @param width New width
     * @param height New height
     * @return Snapshot after resizing
     */
    fun resizeFigure(
        index: Int,
        width: Double,
        height: Double,
    ): CanvasSnapshot

    /**
     * Deletes all currently selected figures.
     *
     * @return Snapshot after deletion
     */
    fun deleteSelectedFigures(): CanvasSnapshot

    /**
     * Saves the current editable canvas document into the managed workspace.
     *
     * @param requestedName Preferred document filename
     * @return Persisted canvas document reference
     */
    fun saveDocument(requestedName: String): CanvasDocumentReference

    /**
     * Opens an editable canvas document from the managed workspace.
     *
     * @param id Optional workspace file ID
     * @param path Optional workspace-relative file path
     * @return Snapshot after loading the document
     */
    fun openDocument(
        id: String?,
        path: String?,
    ): CanvasSnapshot

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
 * @property selectedFigureIndices Draw-order indices of all selected figures
 * @property figures Ordered figure summaries
 */
@Serializable
data class CanvasSnapshot(
    val figureCount: Int,
    val zoomPercent: Int,
    val gridVisible: Boolean,
    val selectedFigureIndices: Set<Int> = emptySet(),
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
    val content: String = "",
    val color: String = "",
    val strokeWidth: Double = 1.0,
    val points: List<CanvasPoint> = emptyList(),
)

/**
 * Serializable point used by freehand stroke figures.
 *
 * @property x Canvas X coordinate
 * @property y Canvas Y coordinate
 */
@Serializable
data class CanvasPoint(
    val x: Double,
    val y: Double,
)

/**
 * Parses a `#RRGGBB`, `RRGGBB`, `#AARRGGBB`, or `AARRGGBB` string into a packed ARGB `Int`.
 *
 * Returns `null` for empty input, malformed hex, or any other length so callers can fall back
 * to a default. Used by the Compose surface and the PNG renderer to keep one parser in sync.
 *
 * @return ARGB int with full alpha when only RGB is provided, or `null` if the input is invalid
 */
fun parseHexColor(hex: String): Int? {
    if (hex.isEmpty()) return null
    val stripped = hex.removePrefix("#")
    val value = stripped.toLongOrNull(16) ?: return null
    return when (stripped.length) {
        6 -> (0xFF000000L or value).toInt()
        8 -> value.toInt()
        else -> null
    }
}

/**
 * Persisted canvas document reference in the managed workspace.
 *
 * @property id Workspace file ID
 * @property relativePath Workspace-relative path
 * @property mimeType Canvas MIME type
 * @property sha256 SHA-256 hash of the serialized document
 */
@Serializable
data class CanvasDocumentReference(
    val id: String,
    val relativePath: String,
    val mimeType: String,
    val sha256: String,
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
