package de.heckenmann.visualagent.agent.tools.api

/** Canvas operations needed by the canvas tool. JSON results preserve the public canvas schema. */
interface CanvasToolPort {
    /** Serializes the current canvas snapshot. */
    fun snapshot(): String

    /** Clears and serializes the canvas. */
    fun clear(): String

    /** Draws text. */
    fun drawText(
        text: String,
        x: Double,
        y: Double,
        color: String,
    ): String

    /** Draws a rectangle. */
    fun drawRect(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        fillColor: String,
        strokeColor: String?,
    ): String

    /** Draws a line. */
    fun drawLine(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        color: String,
        width: Double,
    ): String

    /** Draws a freehand stroke. */
    fun drawStroke(
        points: List<ToolCanvasPoint>,
        color: String,
        width: Double,
    ): String

    /** Draws a circle. */
    fun drawCircle(
        centerX: Double,
        centerY: Double,
        radius: Double,
        fillColor: String,
    ): String

    /** Inserts a workspace image. */
    fun insertImage(path: String): String

    /** Selects figure indices. */
    fun select(indices: Set<Int>): String

    /** Selects at a coordinate. */
    fun selectAt(
        x: Double,
        y: Double,
    ): String

    /** Moves a figure. */
    fun moveFigure(
        index: Int,
        deltaX: Double,
        deltaY: Double,
    ): String

    /** Resizes a figure. */
    fun resizeFigure(
        index: Int,
        width: Double,
        height: Double,
    ): String

    /** Deletes selected figures. */
    fun deleteSelectedFigures(): String

    /** Saves an editable document. */
    fun saveDocument(name: String): String

    /** Opens an editable document. */
    fun openDocument(
        id: String?,
        path: String?,
    ): String

    /** Captures an immutable image. */
    fun captureImage(format: String): ToolCanvasImage

    /** Persists a captured image in conversation history. */
    fun saveCapture(
        sessionId: String,
        image: ToolCanvasImage,
    ): String
}

/** Canvas point crossing the application port. */
data class ToolCanvasPoint(
    val x: Double,
    val y: Double,
)

/** Immutable canvas image crossing the application port. */
data class ToolCanvasImage(
    val format: String,
    val mimeType: String,
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
)
