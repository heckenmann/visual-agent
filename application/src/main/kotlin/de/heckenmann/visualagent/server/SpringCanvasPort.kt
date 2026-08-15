package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.canvas.CanvasOperations
import de.heckenmann.visualagent.protocol.CanvasDocumentReference
import de.heckenmann.visualagent.protocol.CanvasFigureSnapshot
import de.heckenmann.visualagent.protocol.CanvasImageSnapshot
import de.heckenmann.visualagent.protocol.CanvasPoint
import de.heckenmann.visualagent.protocol.CanvasPort
import de.heckenmann.visualagent.protocol.CanvasSnapshot
import org.springframework.stereotype.Component
import de.heckenmann.visualagent.canvas.CanvasDocumentReference as ApplicationCanvasDocumentReference
import de.heckenmann.visualagent.canvas.CanvasFigureSnapshot as ApplicationCanvasFigureSnapshot
import de.heckenmann.visualagent.canvas.CanvasImageSnapshot as ApplicationCanvasImageSnapshot
import de.heckenmann.visualagent.canvas.CanvasPoint as ApplicationCanvasPoint
import de.heckenmann.visualagent.canvas.CanvasSnapshot as ApplicationCanvasSnapshot

/** Maps the application canvas implementation to the neutral [CanvasPort]. */
@Component
class SpringCanvasPort(
    private val canvasOperations: CanvasOperations,
) : CanvasPort {
    override fun snapshot(): CanvasSnapshot = canvasOperations.snapshot().toProtocol()

    override fun clear(): CanvasSnapshot = canvasOperations.clear().toProtocol()

    override fun drawText(
        text: String,
        x: Double,
        y: Double,
        color: String,
    ): CanvasSnapshot = canvasOperations.drawText(text, x, y, color).toProtocol()

    override fun drawRect(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        fillColor: String,
        strokeColor: String?,
    ): CanvasSnapshot = canvasOperations.drawRect(x, y, width, height, fillColor, strokeColor).toProtocol()

    override fun drawLine(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        color: String,
        width: Double,
    ): CanvasSnapshot = canvasOperations.drawLine(x1, y1, x2, y2, color, width).toProtocol()

    override fun drawStroke(
        points: List<CanvasPoint>,
        color: String,
        width: Double,
    ): CanvasSnapshot = canvasOperations.drawStroke(points.map(CanvasPoint::toApplication), color, width).toProtocol()

    override fun drawCircle(
        centerX: Double,
        centerY: Double,
        radius: Double,
        fillColor: String,
    ): CanvasSnapshot = canvasOperations.drawCircle(centerX, centerY, radius, fillColor).toProtocol()

    override fun insertImage(path: String): CanvasSnapshot = canvasOperations.insertImage(path).toProtocol()

    override fun selectFigures(indices: Set<Int>): CanvasSnapshot = canvasOperations.selectFigures(indices).toProtocol()

    override fun selectAt(
        x: Double,
        y: Double,
    ): CanvasSnapshot = canvasOperations.selectAt(x, y).toProtocol()

    override fun moveFigure(
        index: Int,
        deltaX: Double,
        deltaY: Double,
    ): CanvasSnapshot = canvasOperations.moveFigure(index, deltaX, deltaY).toProtocol()

    override fun resizeFigure(
        index: Int,
        width: Double,
        height: Double,
    ): CanvasSnapshot = canvasOperations.resizeFigure(index, width, height).toProtocol()

    override fun deleteSelectedFigures(): CanvasSnapshot = canvasOperations.deleteSelectedFigures().toProtocol()

    override fun saveDocument(requestedName: String): CanvasDocumentReference = canvasOperations.saveDocument(requestedName).toProtocol()

    override fun openDocument(
        id: String?,
        path: String?,
    ): CanvasSnapshot = canvasOperations.openDocument(id, path).toProtocol()

    override fun captureImage(format: String): CanvasImageSnapshot = canvasOperations.captureImage(format).toProtocol()
}

private fun ApplicationCanvasSnapshot.toProtocol(): CanvasSnapshot =
    CanvasSnapshot(
        figureCount = figureCount,
        zoomPercent = zoomPercent,
        gridVisible = gridVisible,
        selectedFigureIndices = selectedFigureIndices,
        figures = figures.map(ApplicationCanvasFigureSnapshot::toProtocol),
    )

private fun ApplicationCanvasFigureSnapshot.toProtocol(): CanvasFigureSnapshot =
    CanvasFigureSnapshot(
        index = index,
        type = type,
        x = x,
        y = y,
        width = width,
        height = height,
        content = content,
        color = color,
        strokeWidth = strokeWidth,
        points = points.map(ApplicationCanvasPoint::toProtocol),
    )

private fun ApplicationCanvasPoint.toProtocol(): CanvasPoint = CanvasPoint(x, y)

private fun CanvasPoint.toApplication(): ApplicationCanvasPoint = ApplicationCanvasPoint(x, y)

private fun ApplicationCanvasDocumentReference.toProtocol(): CanvasDocumentReference =
    CanvasDocumentReference(id, relativePath, mimeType, sha256)

private fun ApplicationCanvasImageSnapshot.toProtocol(): CanvasImageSnapshot = CanvasImageSnapshot(format, mimeType, bytes, width, height)
