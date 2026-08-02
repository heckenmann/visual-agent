package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.canvas.CanvasDocumentReference
import de.heckenmann.visualagent.canvas.CanvasFigureSnapshot
import de.heckenmann.visualagent.canvas.CanvasImageSnapshot
import de.heckenmann.visualagent.canvas.CanvasOperations
import de.heckenmann.visualagent.canvas.CanvasPoint
import de.heckenmann.visualagent.canvas.CanvasSnapshot
import de.heckenmann.visualagent.knowledge.ConversationRecord
import de.heckenmann.visualagent.knowledge.ConversationStore

internal class FakeCanvasOperations : CanvasOperations {
    val actions = mutableListOf<String>()
    private var figures = emptyList<CanvasFigureSnapshot>()

    val lastFigure: CanvasFigureSnapshot
        get() = figures.last()

    override fun snapshot(): CanvasSnapshot = snapshotOf(figures)

    override fun clear(): CanvasSnapshot {
        actions += "clear"
        figures = emptyList()
        return snapshot()
    }

    override fun drawText(
        text: String,
        x: Double,
        y: Double,
        color: String,
    ): CanvasSnapshot {
        actions += "drawText"
        figures = listOf(CanvasFigureSnapshot(0, "text", x, y, 0.0, 0.0))
        return snapshot()
    }

    override fun drawRect(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        fillColor: String,
        strokeColor: String?,
    ): CanvasSnapshot {
        actions += "drawRect"
        figures = listOf(CanvasFigureSnapshot(0, "rectangle", x, y, width, height))
        return snapshot()
    }

    override fun drawLine(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        color: String,
        width: Double,
    ): CanvasSnapshot {
        actions += "drawLine"
        figures = listOf(CanvasFigureSnapshot(0, "line", x1, y1, x2 - x1, y2 - y1))
        return snapshot()
    }

    override fun drawCircle(
        centerX: Double,
        centerY: Double,
        radius: Double,
        fillColor: String,
    ): CanvasSnapshot {
        actions += "drawCircle"
        figures = listOf(CanvasFigureSnapshot(0, "circle", centerX - radius, centerY - radius, radius * 2, radius * 2))
        return snapshot()
    }

    override fun insertImage(path: String): CanvasSnapshot {
        actions += "insertImage"
        figures = listOf(CanvasFigureSnapshot(0, "image", 40.0, 40.0, 100.0, 100.0))
        return snapshot()
    }

    override fun drawStroke(
        points: List<CanvasPoint>,
        color: String,
        width: Double,
    ): CanvasSnapshot {
        actions += "drawStroke"
        require(points.size >= 2) { "drawStroke requires at least two points" }
        val first = points.first()
        val last = points.last()
        figures =
            listOf(
                CanvasFigureSnapshot(
                    index = 0,
                    type = "stroke",
                    x = first.x,
                    y = first.y,
                    width = last.x - first.x,
                    height = last.y - first.y,
                    content = "",
                    color = color,
                    strokeWidth = width,
                    points = points,
                ),
            )
        return snapshot()
    }

    override fun selectFigures(indices: Set<Int>): CanvasSnapshot {
        actions += "select:${indices.sorted()}"
        return snapshot()
    }

    override fun selectAt(
        x: Double,
        y: Double,
    ): CanvasSnapshot {
        actions += "selectAt"
        return snapshot()
    }

    override fun moveFigure(
        index: Int,
        deltaX: Double,
        deltaY: Double,
    ): CanvasSnapshot {
        actions += "moveFigure"
        figures =
            figures.mapIndexed { figureIndex, figure ->
                if (figureIndex == index) figure.copy(x = figure.x + deltaX, y = figure.y + deltaY) else figure
            }
        return snapshot()
    }

    override fun resizeFigure(
        index: Int,
        width: Double,
        height: Double,
    ): CanvasSnapshot {
        actions += "resizeFigure"
        figures =
            figures.mapIndexed { figureIndex, figure ->
                if (figureIndex == index) figure.copy(width = width, height = height) else figure
            }
        return snapshot()
    }

    override fun deleteSelectedFigures(): CanvasSnapshot {
        actions += "deleteSelectedFigures"
        figures = emptyList()
        return snapshot()
    }

    override fun saveDocument(requestedName: String): CanvasDocumentReference {
        actions += "saveDocument:$requestedName"
        return CanvasDocumentReference(
            id = "canvas-1",
            relativePath = "canvas/$requestedName.canvas",
            mimeType = "application/vnd.visual-agent.canvas+xml",
            sha256 = "abc123",
        )
    }

    override fun openDocument(
        id: String?,
        path: String?,
    ): CanvasSnapshot {
        actions += "openDocument:$id:$path"
        figures = listOf(CanvasFigureSnapshot(0, "rectangle", 1.0, 2.0, 3.0, 4.0))
        return snapshot()
    }

    override fun captureImage(format: String): CanvasImageSnapshot {
        require(format == "png") { "Unsupported canvas image format: $format" }
        actions += "captureImage:$format"
        return CanvasImageSnapshot(
            format = "png",
            mimeType = "image/png",
            bytes = byteArrayOf(1, 2, 3),
            width = 2,
            height = 1,
        )
    }

    private fun snapshotOf(figures: List<CanvasFigureSnapshot>): CanvasSnapshot =
        CanvasSnapshot(
            figureCount = figures.size,
            zoomPercent = 100,
            gridVisible = true,
            selectedFigureIndices = emptySet(),
            figures = figures,
        )
}

internal class FakeConversationStore : ConversationStore {
    val saved = mutableListOf<SavedMessage>()

    override fun saveConversationMessage(
        sessionId: String,
        role: String,
        content: String,
        metadata: String?,
    ): String {
        saved += SavedMessage(sessionId, role, content, metadata)
        return "message-${saved.size}"
    }

    override fun getConversationMessages(
        sessionId: String,
        limit: Int,
    ): List<ConversationRecord> = emptyList()

    override fun getConversationMessagesPage(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): List<ConversationRecord> = emptyList()

    override fun searchConversationMessages(
        sessionId: String,
        query: String,
        limit: Int,
    ): List<ConversationRecord> = emptyList()

    override fun deleteConversationMessages(sessionId: String): Int = 0

    override fun deleteConversationMessageById(id: String): Int = 0

    override fun updateConversationMessageContent(
        id: String,
        newContent: String,
    ): Int = 0
}

internal data class SavedMessage(
    val sessionId: String,
    val role: String,
    val content: String,
    val metadata: String?,
)
