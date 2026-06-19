package de.heckenmann.visualagent.ui.panels.canvas

import javafx.scene.control.ContextMenu
import javafx.scene.control.MenuItem
import javafx.scene.input.ContextMenuEvent
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.layout.StackPane
import org.jhotdraw8.draw.SimpleDrawingView
import org.jhotdraw8.draw.figure.Figure

internal class CanvasSelectionDeletionController(
    private val editorViewport: StackPane,
    private val drawingView: SimpleDrawingView,
    private val activeDrawing: () -> Figure,
    private val activeLayer: () -> Figure,
    private val activeTool: () -> CanvasTool,
    private val persistDocument: () -> Unit,
    private val updateHistoryActions: () -> Unit,
    private val updateSelectionActions: (Boolean) -> Unit,
) {
    private val deleteSelectionMenuItem =
        MenuItem("Delete selection").apply {
            setOnAction { deleteSelectedFigures() }
        }
    private val contextMenu = ContextMenu(deleteSelectionMenuItem)

    fun install() {
        updateSelectionActions(hasDeletableSelection())
        editorViewport.addEventFilter(MouseEvent.MOUSE_PRESSED) { editorViewport.requestFocus() }
        editorViewport.addEventFilter(MouseEvent.MOUSE_RELEASED) { updateSelectionActions(hasDeletableSelection()) }
        editorViewport.addEventFilter(KeyEvent.KEY_PRESSED) { event ->
            if (event.code != KeyCode.DELETE && event.code != KeyCode.BACK_SPACE) return@addEventFilter
            if (deleteSelectedFigures()) {
                event.consume()
            }
        }
        editorViewport.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED) { event ->
            editorViewport.requestFocus()
            selectFigureAt(event.sceneX, event.sceneY)
            updateSelectionActions(hasDeletableSelection())
            deleteSelectionMenuItem.isDisable = !hasDeletableSelection()
            contextMenu.show(editorViewport, event.screenX, event.screenY)
            event.consume()
        }
    }

    fun deleteSelectedFigures(): Boolean {
        val selected = deletableSelection()
        if (selected.isEmpty()) return false
        selected.forEach(drawingView.model::removeFromParent)
        drawingView.selectedFigures.removeAll(selected.toSet())
        drawingView.recreateHandles()
        persistDocument()
        updateHistoryActions()
        updateSelectionActions(hasDeletableSelection())
        return true
    }

    fun refreshSelectionActions() {
        updateSelectionActions(hasDeletableSelection())
    }

    private fun deletableSelection(): List<Figure> =
        drawingView.selectedFigures
            .filter { it.isDeletable && it.parent != null && it !== activeDrawing() && it !== activeLayer() }

    private fun selectFigureAt(
        sceneX: Double,
        sceneY: Double,
    ) {
        if (activeTool() != CanvasTool.SELECT) return
        val viewPoint = drawingView.node.sceneToLocal(sceneX, sceneY)
        val figure = drawingView.findFigure(viewPoint.x, viewPoint.y) ?: return
        if (figure !in drawingView.selectedFigures) {
            drawingView.selectedFigures.clear()
            drawingView.selectedFigures.add(figure)
            drawingView.recreateHandles()
            updateSelectionActions(hasDeletableSelection())
        }
    }

    private fun hasDeletableSelection(): Boolean = deletableSelection().isNotEmpty()
}
