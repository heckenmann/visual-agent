package de.heckenmann.visualagent.protocol

/** Toolkit-neutral canvas operations exposed across the UI/application boundary. */
interface CanvasPort {
    /** Reads the current canvas snapshot. */
    fun snapshot(): CanvasSnapshot

    /** Removes all figures and returns the resulting snapshot. */
    fun clear(): CanvasSnapshot

    /** Adds editable text and returns the resulting snapshot. */
    fun drawText(
        text: String,
        x: Double,
        y: Double,
        color: String,
    ): CanvasSnapshot

    /** Adds an editable rectangle and returns the resulting snapshot. */
    fun drawRect(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        fillColor: String,
        strokeColor: String?,
    ): CanvasSnapshot

    /** Adds an editable line and returns the resulting snapshot. */
    fun drawLine(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        color: String,
        width: Double,
    ): CanvasSnapshot

    /** Adds a freehand stroke and returns the resulting snapshot. */
    fun drawStroke(
        points: List<CanvasPoint>,
        color: String,
        width: Double,
    ): CanvasSnapshot

    /** Adds an editable circle and returns the resulting snapshot. */
    fun drawCircle(
        centerX: Double,
        centerY: Double,
        radius: Double,
        fillColor: String,
    ): CanvasSnapshot

    /** Adds an image figure from a managed workspace-relative path. */
    fun insertImage(path: String): CanvasSnapshot

    /** Replaces the current selection with the supplied figure indices. */
    fun selectFigures(indices: Set<Int>): CanvasSnapshot

    /** Selects the top-most figure at the supplied canvas coordinate. */
    fun selectAt(
        x: Double,
        y: Double,
    ): CanvasSnapshot

    /** Moves one figure by a delta and selects it. */
    fun moveFigure(
        index: Int,
        deltaX: Double,
        deltaY: Double,
    ): CanvasSnapshot

    /** Resizes one figure and selects it. */
    fun resizeFigure(
        index: Int,
        width: Double,
        height: Double,
    ): CanvasSnapshot

    /** Deletes all currently selected figures. */
    fun deleteSelectedFigures(): CanvasSnapshot

    /** Saves the current editable document into the managed workspace. */
    fun saveDocument(requestedName: String): CanvasDocumentReference

    /** Opens an editable document by ID or relative path. */
    fun openDocument(
        id: String?,
        path: String?,
    ): CanvasSnapshot

    /** Captures the current canvas as encoded image bytes. */
    fun captureImage(format: String): CanvasImageSnapshot
}

/** Immutable canvas state transferred to the presentation client. */
data class CanvasSnapshot(
    val figureCount: Int,
    val zoomPercent: Int,
    val gridVisible: Boolean,
    val selectedFigureIndices: Set<Int> = emptySet(),
    val figures: List<CanvasFigureSnapshot>,
)

/** Immutable summary of one canvas figure. */
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

/** Point used by freehand canvas strokes. */
data class CanvasPoint(
    val x: Double,
    val y: Double,
)

/** Reference to a persisted editable canvas document. */
data class CanvasDocumentReference(
    val id: String,
    val relativePath: String,
    val mimeType: String,
    val sha256: String,
)

/** Encoded image produced by a canvas capture operation. */
data class CanvasImageSnapshot(
    val format: String,
    val mimeType: String,
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
)
