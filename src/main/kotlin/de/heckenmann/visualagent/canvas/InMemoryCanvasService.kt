package de.heckenmann.visualagent.canvas

import de.heckenmann.visualagent.workspace.WorkspaceFilePaths
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import org.springframework.stereotype.Service
import kotlin.io.path.readText
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Toolkit-neutral canvas service used by model tools during the Compose migration.
 *
 * The service preserves the existing tool-call contract while the visual editor is rebuilt with Compose-native rendering
 * and persistence.
 */
@Service
class InMemoryCanvasService(
    internal val workspaceFileService: WorkspaceFileService,
) : CanvasOperations {
    private val lock = Any()
    internal val figures = mutableListOf<CanvasFigureSnapshot>().apply { addAll(loadDefaultDocument()) }
    internal val selectedFigureIndices = mutableSetOf<Int>()

    override fun snapshot(): CanvasSnapshot =
        synchronized(lock) {
            toSnapshot()
        }

    override fun clear(): CanvasSnapshot =
        synchronized(lock) {
            figures.clear()
            selectedFigureIndices.clear()
            toSnapshot().also { persistDefaultDocument(it) }
        }

    override fun drawText(
        text: String,
        x: Double,
        y: Double,
        color: String,
    ): CanvasSnapshot =
        synchronized(lock) {
            addFigure(type = "text", x = x, y = y, width = max(80.0, text.length * 8.0), height = 24.0, content = text, color = color)
            toSnapshot().also { persistDefaultDocument(it) }
        }

    override fun drawRect(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        fillColor: String,
        strokeColor: String?,
    ): CanvasSnapshot =
        synchronized(lock) {
            addFigure(type = "rectangle", x = x, y = y, width = width, height = height, color = fillColor)
            toSnapshot().also { persistDefaultDocument(it) }
        }

    override fun drawLine(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        color: String,
        width: Double,
    ): CanvasSnapshot =
        synchronized(lock) {
            addFigure(
                type = "line",
                x = minOf(x1, x2),
                y = minOf(y1, y2),
                width = kotlin.math.abs(x2 - x1).coerceAtLeast(width),
                height = kotlin.math.abs(y2 - y1).coerceAtLeast(width),
                color = color,
                strokeWidth = width,
            )
            toSnapshot().also { persistDefaultDocument(it) }
        }

    override fun drawStroke(
        points: List<CanvasPoint>,
        color: String,
        width: Double,
    ): CanvasSnapshot =
        synchronized(lock) {
            val normalizedPoints = points.distinctAdjacent().take(MAX_STROKE_POINTS)
            require(normalizedPoints.size >= 2) { "A freehand stroke requires at least two points" }
            val minX = normalizedPoints.minOf(CanvasPoint::x)
            val minY = normalizedPoints.minOf(CanvasPoint::y)
            val maxX = normalizedPoints.maxOf(CanvasPoint::x)
            val maxY = normalizedPoints.maxOf(CanvasPoint::y)
            addFigure(
                type = "stroke",
                x = minX,
                y = minY,
                width = (maxX - minX).coerceAtLeast(width),
                height = (maxY - minY).coerceAtLeast(width),
                color = color,
                strokeWidth = width,
                points = normalizedPoints.map { CanvasPoint(it.x - minX, it.y - minY) },
            )
            toSnapshot().also { persistDefaultDocument(it) }
        }

    override fun drawCircle(
        centerX: Double,
        centerY: Double,
        radius: Double,
        fillColor: String,
    ): CanvasSnapshot =
        synchronized(lock) {
            addFigure(
                type = "circle",
                x = centerX - radius,
                y = centerY - radius,
                width = radius * 2.0,
                height = radius * 2.0,
            )
            toSnapshot().also { persistDefaultDocument(it) }
        }

    override fun insertImage(path: String): CanvasSnapshot =
        synchronized(lock) {
            addFigure(type = "image", x = 0.0, y = 0.0, width = 320.0, height = 240.0, content = path)
            toSnapshot().also { persistDefaultDocument(it) }
        }

    override fun selectFigures(indices: Set<Int>): CanvasSnapshot =
        synchronized(lock) {
            selectedFigureIndices.clear()
            selectedFigureIndices.addAll(indices.filter { it in figures.indices })
            toSnapshot()
        }

    override fun selectAt(
        x: Double,
        y: Double,
    ): CanvasSnapshot =
        synchronized(lock) {
            selectedFigureIndices.clear()
            figures
                .asReversed()
                .firstOrNull { it.contains(x, y) }
                ?.index
                ?.let(selectedFigureIndices::add)
            toSnapshot()
        }

    override fun moveFigure(
        index: Int,
        deltaX: Double,
        deltaY: Double,
    ): CanvasSnapshot =
        synchronized(lock) {
            val figure = requireFigure(index)
            figures[index] = figure.copy(x = figure.x + deltaX, y = figure.y + deltaY)
            selectedFigureIndices.clear()
            selectedFigureIndices.add(index)
            toSnapshot().also { persistDefaultDocument(it) }
        }

    override fun resizeFigure(
        index: Int,
        width: Double,
        height: Double,
    ): CanvasSnapshot =
        synchronized(lock) {
            val figure = requireFigure(index)
            figures[index] =
                figure.copy(
                    width = width.coerceAtLeast(MIN_FIGURE_SIZE),
                    height = height.coerceAtLeast(MIN_FIGURE_SIZE),
                )
            selectedFigureIndices.clear()
            selectedFigureIndices.add(index)
            toSnapshot().also { persistDefaultDocument(it) }
        }

    override fun deleteSelectedFigures(): CanvasSnapshot =
        synchronized(lock) {
            val sorted = selectedFigureIndices.sortedDescending()
            for (index in sorted) {
                if (index in figures.indices) {
                    figures.removeAt(index)
                }
            }
            reindexFigures()
            selectedFigureIndices.clear()
            toSnapshot().also { persistDefaultDocument(it) }
        }

    override fun saveDocument(requestedName: String): CanvasDocumentReference {
        val record =
            workspaceFileService.createManagedFile(
                directoryName = CANVAS_DIRECTORY,
                requestedName = requestedName.ifBlank { DEFAULT_EXPLICIT_DOCUMENT_NAME }.withCanvasExtension(),
                bytes = CanvasDocumentCodec.encode(snapshot()).toByteArray(Charsets.UTF_8),
                mimeType = WorkspaceFilePaths.CANVAS_MIME_TYPE,
            )
        return CanvasDocumentReference(
            id = record.id,
            relativePath = record.relativePath,
            mimeType = record.mimeType,
            sha256 = record.sha256,
        )
    }

    override fun openDocument(
        id: String?,
        path: String?,
    ): CanvasSnapshot =
        synchronized(lock) {
            val record = workspaceFileService.requireFile(id, path)
            require(record.mimeType == WorkspaceFilePaths.CANVAS_MIME_TYPE) { "Workspace file is not a canvas document" }
            val document = CanvasDocumentCodec.decode(workspaceFileService.resolveManagedPath(record.relativePath).readText(Charsets.UTF_8))
            figures.clear()
            figures.addAll(document.figures)
            selectedFigureIndices.clear()
            toSnapshot().also { persistDefaultDocument(it) }
        }

    override fun captureImage(format: String): CanvasImageSnapshot {
        val normalizedFormat = format.lowercase().ifBlank { "png" }
        require(normalizedFormat == "png") { "Only PNG canvas capture is supported during the Compose migration" }
        val snapshot = snapshot()
        val width =
            max(
                800,
                snapshot.figures.maxOfOrNull { (it.x + it.width + 40.0).roundToInt() } ?: 800,
            )
        val height =
            max(
                600,
                snapshot.figures.maxOfOrNull { (it.y + it.height + 40.0).roundToInt() } ?: 600,
            )
        return CanvasImageSnapshot(
            format = "png",
            mimeType = "image/png",
            bytes = CanvasPngRenderer.render(snapshot, width, height),
            width = width,
            height = height,
        )
    }

    internal companion object {
        const val CANVAS_DIRECTORY = "canvas"
        const val DEFAULT_DOCUMENT_NAME = "current.canvas"
        const val DEFAULT_EXPLICIT_DOCUMENT_NAME = "canvas.canvas"
        const val MIN_FIGURE_SIZE = 8.0
        const val MAX_STROKE_POINTS = 2000
    }
}
