package de.heckenmann.visualagent.ui.panels.canvas

import de.heckenmann.visualagent.knowledge.PreferenceStore
import javafx.animation.PauseTransition
import javafx.embed.swing.SwingFXUtils
import javafx.geometry.Point2D
import javafx.scene.SnapshotParameters
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.scene.shape.Polyline
import javafx.util.Duration
import org.jhotdraw8.draw.SimpleDrawingEditor
import org.jhotdraw8.draw.SimpleDrawingView
import org.jhotdraw8.draw.constrain.GridConstrainer
import org.jhotdraw8.draw.figure.Figure
import org.jhotdraw8.draw.figure.LayerFigure
import org.jhotdraw8.draw.figure.SimpleLayeredDrawing
import org.jhotdraw8.draw.handle.HandleType
import org.jhotdraw8.draw.undo.DrawingModelUndoAdapter
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.io.File
import javax.imageio.ImageIO

/**
 * Hosts the JHotDraw-based structured drawing editor.
 *
 * Figures remain editable after insertion and participate in JHotDraw selection,
 * resize, rotation, zoom, grid, and undo/redo behavior.
 */
@Component
@Lazy
class CanvasPanel(
    preferenceStore: PreferenceStore,
) : Region() {
    private val rootBorderPane = BorderPane()
    private val drawingView = SimpleDrawingView()
    private val drawingEditor = SimpleDrawingEditor()
    private val selectionTool = createSelectionTool()
    private val gridConstrainer = GridConstrainer(25.0, 25.0)
    private val strokePreview =
        Polyline().apply {
            isManaged = false
            isMouseTransparent = true
            fill = null
            stroke = Color.web(PEN_COLOR)
            strokeWidth = PEN_WIDTH
        }
    private val editorViewport = StackPane(drawingView.node, strokePreview)
    private val undoAdapter = DrawingModelUndoAdapter(drawingView.model)
    private val persistence = CanvasDocumentPersistence(preferenceStore)
    private val persistenceDelay = PauseTransition(Duration.millis(250.0))
    private var activeDrawing = SimpleLayeredDrawing()
    private var activeLayer = LayerFigure()
    private var restoringDocument = false
    private var pendingStroke: MutableList<Point2D>? = null
    private var activeTool = CanvasTool.SELECT
    private val toolbar =
        CanvasToolbar(
            onSelect = { selectTool(CanvasTool.SELECT) },
            onPen = { selectTool(CanvasTool.PEN) },
            onInsertImage = ::insertImage,
            onDeleteSelection = ::deleteSelectedFigures,
            onUndo = ::undo,
            onRedo = ::redo,
            onZoomOut = { setZoom(drawingView.zoomFactor - 0.1) },
            onZoomReset = { setZoom(1.0) },
            onZoomIn = { setZoom(drawingView.zoomFactor + 0.1) },
            onGridChanged = { gridConstrainer.drawGridProperty().set(it) },
            onClear = ::clearCanvas,
            onExport = ::exportPng,
        )
    private val selectionDeletionController =
        CanvasSelectionDeletionController(
            editorViewport = editorViewport,
            drawingView = drawingView,
            activeDrawing = { activeDrawing },
            activeLayer = { activeLayer },
            activeTool = { activeTool },
            persistDocument = ::persistDocument,
            updateHistoryActions = ::updateHistoryActions,
            updateSelectionActions = toolbar::updateSelectionActions,
        )

    init {
        configureEditor()
        setupUI()
        installPenInput()
        selectionDeletionController.install()
        updateHistoryActions()
    }

    private fun configureEditor() {
        restoreDrawing()
        drawingEditor.addDrawingView(drawingView)
        drawingEditor.defaultTool = selectionTool
        drawingEditor.activeTool = selectionTool
        drawingEditor.handleType = HandleType.RESIZE
        drawingEditor.anchorHandleType = HandleType.RESIZE
        drawingEditor.leadHandleType = HandleType.RESIZE
        drawingView.constrainer = gridConstrainer
        gridConstrainer.drawGridProperty().set(true)
        gridConstrainer.snapToGridProperty().set(false)
        undoAdapter.addUndoEditListener(drawingEditor.undoManager)
        drawingEditor.undoManager.undoableProperty().addListener { _, _, _ -> updateHistoryActions() }
        drawingEditor.undoManager.redoableProperty().addListener { _, _, _ -> updateHistoryActions() }
        drawingView.model.addTreeModelListener { schedulePersistence() }
        drawingView.model.addDrawingModelListener { schedulePersistence() }
        persistenceDelay.setOnFinished { persistDocument() }
    }

    private fun restoreDrawing() {
        restoringDocument = true
        activeDrawing = runCatching(persistence::load).getOrNull() ?: SimpleLayeredDrawing()
        activeLayer =
            activeDrawing.children.filterIsInstance<LayerFigure>().firstOrNull()
                ?: LayerFigure().also(activeDrawing::addChild)
        drawingView.model.setRoot(activeDrawing)
        restoringDocument = false
    }

    private fun setupUI() {
        styleClass.add("canvas-panel")
        rootBorderPane.top = toolbar
        rootBorderPane.center = editorViewport
        rootBorderPane.styleClass.add("canvas-root")
        editorViewport.styleClass.add("canvas-viewport")
        editorViewport.isFocusTraversable = true
        drawingView.node.styleClass.add("canvas-surface")
        children.add(rootBorderPane)
        minHeight = 100.0
        prefHeight = 600.0
        maxHeight = Double.MAX_VALUE
        selectTool(CanvasTool.SELECT)
    }

    private fun installPenInput() {
        editorViewport.addEventFilter(MouseEvent.MOUSE_PRESSED) { event ->
            if (activeTool != CanvasTool.PEN || event.button != MouseButton.PRIMARY) return@addEventFilter
            pendingStroke = mutableListOf(worldPoint(event))
            strokePreview.points.setAll(event.x, event.y)
            event.consume()
        }
        editorViewport.addEventFilter(MouseEvent.MOUSE_DRAGGED) { event ->
            val points = pendingStroke ?: return@addEventFilter
            points += worldPoint(event)
            strokePreview.points.addAll(event.x, event.y)
            event.consume()
        }
        editorViewport.addEventFilter(MouseEvent.MOUSE_RELEASED) { event ->
            if (event.button != MouseButton.PRIMARY || pendingStroke == null) return@addEventFilter
            commitPendingStroke()
            event.consume()
        }
    }

    /** Removes all figures from the drawing as one undoable operation. */
    fun clearCanvas() {
        drawingView.selectedFigures.clear()
        activeDrawing = SimpleLayeredDrawing()
        activeLayer = LayerFigure()
        activeDrawing.addChild(activeLayer)
        drawingView.model.setRoot(activeDrawing)
        persistDocument()
        updateHistoryActions()
    }

    /**
     * Removes all figures and returns the resulting model-facing canvas snapshot.
     *
     * @return Snapshot after clearing the canvas
     */
    fun clearCanvasAndSnapshot(): CanvasSnapshot {
        clearCanvas()
        return snapshot()
    }

    /** Adds editable text at the supplied drawing coordinates. */
    fun drawText(
        text: String,
        x: Double,
        y: Double,
        color: String = "#24292f",
    ) {
        addFigure(CanvasFigureFactory.text(text, x, y, color))
    }

    /** Adds an editable rectangle with optional outline. */
    fun drawRect(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        fillColor: String,
        strokeColor: String? = null,
    ) {
        addFigure(CanvasFigureFactory.rectangle(x, y, width, height, fillColor, strokeColor))
    }

    /** Adds an editable line between two drawing coordinates. */
    fun drawLine(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        color: String = PEN_COLOR,
        width: Double = PEN_WIDTH,
    ) {
        addFigure(CanvasFigureFactory.line(x1, y1, x2, y2, color, width))
    }

    /** Adds an editable circle around the supplied center. */
    fun drawCircle(
        centerX: Double,
        centerY: Double,
        radius: Double,
        fillColor: String,
    ) {
        addFigure(CanvasFigureFactory.circle(centerX, centerY, radius, fillColor))
    }

    @GeneratedUiGlue
    private fun insertImage() {
        addImage(CanvasFileDialogs.showImageOpenDialog(scene?.window) ?: return)
    }

    internal fun addImage(file: File) {
        val figure = CanvasFigureFactory.image(file)
        addFigure(figure)
        drawingView.selectedFigures.clear()
        drawingView.selectedFigures.add(figure)
        selectTool(CanvasTool.SELECT)
        drawingView.recreateHandles()
        selectionDeletionController.refreshSelectionActions()
    }

    /**
     * Deletes all currently selected figures that can be removed from the drawing.
     *
     * @return true when at least one figure was deleted
     */
    fun deleteSelectedFigures(): Boolean = selectionDeletionController.deleteSelectedFigures()

    /**
     * Returns a compact, serializable summary of the current canvas state.
     *
     * @return Current canvas snapshot
     */
    fun snapshot(): CanvasSnapshot {
        val figures =
            activeLayer.children
                .mapIndexed { index, figure -> figure.toCanvasSnapshot(index) }
        return CanvasSnapshot(
            figureCount = figures.size,
            zoomPercent = (drawingView.zoomFactor * 100).toInt(),
            gridVisible = gridConstrainer.drawGridProperty().get(),
            figures = figures,
        )
    }

    private fun addFigure(figure: Figure) {
        drawingView.model.insertChildAt(figure, activeLayer, activeLayer.children.size)
        updateHistoryActions()
    }

    private fun commitPendingStroke() {
        val points = pendingStroke.orEmpty()
        pendingStroke = null
        strokePreview.points.clear()
        if (points.size < 2) return
        addFigure(CanvasFigureFactory.stroke(points))
    }

    private fun worldPoint(event: MouseEvent): Point2D {
        val viewPoint = drawingView.node.sceneToLocal(event.sceneX, event.sceneY)
        return drawingView.viewToWorld(viewPoint)
    }

    private fun selectTool(tool: CanvasTool) {
        activeTool = tool
        drawingEditor.activeTool = if (tool == CanvasTool.SELECT) selectionTool else null
        toolbar.selectTool(tool)
    }

    private fun setZoom(value: Double) {
        drawingView.zoomFactor = value.coerceIn(0.25, 4.0)
        toolbar.updateZoom((drawingView.zoomFactor * 100).toInt())
    }

    private fun undo() {
        drawingEditor.undoManager.undo()
        syncActiveDrawing()
    }

    private fun redo() {
        drawingEditor.undoManager.redo()
        syncActiveDrawing()
    }

    private fun syncActiveDrawing() {
        activeDrawing =
            drawingView.drawing as? SimpleLayeredDrawing
                ?: SimpleLayeredDrawing().also(drawingView.model::setRoot)
        activeLayer =
            activeDrawing.children.filterIsInstance<LayerFigure>().firstOrNull()
                ?: LayerFigure().also(activeDrawing::addChild)
        updateHistoryActions()
    }

    private fun schedulePersistence() {
        if (!restoringDocument) persistenceDelay.playFromStart()
    }

    private fun persistDocument() {
        if (!restoringDocument) runCatching { persistence.save(activeDrawing) }
    }

    private fun updateHistoryActions() {
        toolbar.updateHistoryActions(drawingEditor.undoManager.canUndo(), drawingEditor.undoManager.canRedo())
    }

    @GeneratedUiGlue
    private fun exportPng() {
        val file = CanvasFileDialogs.showPngSaveDialog(scene?.window) ?: return
        drawingView.clearSelection()
        val snapshot = drawingView.node.snapshot(SnapshotParameters(), null)
        ImageIO.write(SwingFXUtils.fromFXImage(snapshot, null), "png", file)
    }

    override fun layoutChildren() {
        rootBorderPane.resizeRelocate(0.0, 0.0, width, height)
    }
}
