package de.heckenmann.visualagent.canvas

import de.heckenmann.visualagent.error.CanvasOperationException
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

internal fun InMemoryCanvasService.addFigure(
    type: String,
    x: Double,
    y: Double,
    width: Double,
    height: Double,
    content: String = "",
    color: String = "",
    strokeWidth: Double = 1.0,
    points: List<CanvasPoint> = emptyList(),
) {
    figures +=
        CanvasFigureSnapshot(
            index = figures.size,
            type = type,
            x = x,
            y = y,
            width = width,
            height = height,
            content = content,
            color = color,
            strokeWidth = strokeWidth,
            points = points,
        )
    selectedFigureIndices.clear()
    selectedFigureIndices.add(figures.lastIndex)
}

internal fun InMemoryCanvasService.requireFigure(index: Int): CanvasFigureSnapshot =
    figures.getOrNull(index)
        ?: throw CanvasOperationException(
            summary = "Figure not found",
            detail = "The requested canvas figure does not exist. Select an existing figure and try again.",
            retryable = false,
        )

internal fun InMemoryCanvasService.reindexFigures() {
    for (index in figures.indices) {
        figures[index] = figures[index].copy(index = index)
    }
}

internal fun InMemoryCanvasService.toSnapshot(): CanvasSnapshot =
    CanvasSnapshot(
        figureCount = figures.size,
        zoomPercent = 100,
        gridVisible = true,
        selectedFigureIndices = selectedFigureIndices.filter { it in figures.indices }.toSortedSet(),
        figures = figures.toList(),
    )

internal fun CanvasFigureSnapshot.contains(
    pointX: Double,
    pointY: Double,
): Boolean =
    pointX >= x &&
        pointY >= y &&
        pointX <= x + width &&
        pointY <= y + height

internal fun InMemoryCanvasService.loadDefaultDocument(): List<CanvasFigureSnapshot> {
    val path = defaultDocumentPath()
    return if (path.exists()) {
        runCatching { CanvasDocumentCodec.decode(path.readText(Charsets.UTF_8)).figures }.getOrDefault(emptyList())
    } else {
        emptyList()
    }
}

internal fun InMemoryCanvasService.persistDefaultDocument(snapshot: CanvasSnapshot) {
    val path = defaultDocumentPath()
    path.parent.createDirectories()
    path.writeText(CanvasDocumentCodec.encode(snapshot), Charsets.UTF_8)
}

internal fun InMemoryCanvasService.defaultDocumentPath() =
    workspaceFileService
        .workspaceRoot()
        .resolve(InMemoryCanvasService.CANVAS_DIRECTORY)
        .resolve(InMemoryCanvasService.DEFAULT_DOCUMENT_NAME)

internal fun String.withCanvasExtension(): String = if (endsWith(".canvas", ignoreCase = true)) this else "$this.canvas"

internal fun List<CanvasPoint>.distinctAdjacent(): List<CanvasPoint> {
    val result = mutableListOf<CanvasPoint>()
    forEach { point ->
        if (result.lastOrNull() != point) result += point
    }
    return result
}
