package de.heckenmann.visualagent.ui.panels.canvas

import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.Separator
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid
import org.kordamp.ikonli.javafx.FontIcon

/**
 * Toolbar for selecting canvas tools, history actions, zoom, and export options.
 */
internal class CanvasToolbar(
    onSelect: () -> Unit,
    onPen: () -> Unit,
    onInsertImage: () -> Unit,
    onDeleteSelection: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomReset: () -> Unit,
    onZoomIn: () -> Unit,
    onGridChanged: (Boolean) -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
) : VBox(8.0) {
    private val selectButton = toolButton("Select", FontAwesomeSolid.MOUSE_POINTER, onSelect)
    private val penButton = toolButton("Pen", FontAwesomeSolid.PEN, onPen)
    private val imageButton = toolButton("Image", FontAwesomeSolid.IMAGE, onInsertImage)
    private val deleteButton =
        toolButton("Delete", FontAwesomeSolid.TRASH_ALT, onDeleteSelection).apply {
            isDisable = true
        }
    private val undoButton = toolButton("Undo", FontAwesomeSolid.UNDO, onUndo)
    private val redoButton = toolButton("Redo", FontAwesomeSolid.REDO, onRedo)
    private val zoomResetButton = toolButton("100%", FontAwesomeSolid.SEARCH, onZoomReset)

    init {
        styleClass.add("canvas-toolbar")
        padding = Insets(10.0, 18.0, 10.0, 18.0)

        val title = Label("Canvas").apply { styleClass.add("page-title") }
        val subtitle = Label("Sketch ideas and spatially organize project context.").apply { styleClass.add("page-subtitle") }
        val gridToggle =
            CheckBox("Grid").apply {
                graphic = FontIcon(FontAwesomeSolid.TH)
                graphicTextGap = 6.0
                isSelected = true
                setOnAction { onGridChanged(isSelected) }
            }
        val exportButton =
            toolButton("Export PNG", FontAwesomeSolid.FILE_EXPORT, onExport).apply {
                styleClass.addAll("accent", "canvas-export-button")
            }

        val header = HBox(VBox(1.0, title, subtitle)).apply { styleClass.add("canvas-toolbar-header") }
        val controls =
            HBox(
                8.0,
                selectButton,
                penButton,
                imageButton,
                deleteButton,
                Separator(Orientation.VERTICAL),
                undoButton,
                redoButton,
                Separator(Orientation.VERTICAL),
                toolButton("Out", FontAwesomeSolid.SEARCH_MINUS, onZoomOut),
                zoomResetButton,
                toolButton("In", FontAwesomeSolid.SEARCH_PLUS, onZoomIn),
                gridToggle,
                Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
                toolButton("Clear", FontAwesomeSolid.BROOM, onClear),
                exportButton,
            ).apply { styleClass.add("canvas-toolbar-controls") }
        children.addAll(header, controls)
    }

    fun selectTool(tool: CanvasTool) {
        selectButton.styleClass.remove("active")
        penButton.styleClass.remove("active")
        imageButton.styleClass.remove("active")
        when (tool) {
            CanvasTool.SELECT -> selectButton
            CanvasTool.PEN -> penButton
        }.styleClass.add("active")
    }

    fun updateZoom(percent: Int) {
        zoomResetButton.text = "$percent%"
    }

    fun updateHistoryActions(
        canUndo: Boolean,
        canRedo: Boolean,
    ) {
        undoButton.isDisable = !canUndo
        redoButton.isDisable = !canRedo
    }

    fun updateSelectionActions(hasDeletableSelection: Boolean) {
        deleteButton.isDisable = !hasDeletableSelection
    }

    private fun toolButton(
        text: String,
        icon: FontAwesomeSolid,
        action: () -> Unit,
    ): Button =
        Button(text, FontIcon(icon)).apply {
            styleClass.add("canvas-tool-button")
            graphicTextGap = 6.0
            tooltip = Tooltip(text)
            setOnAction { action() }
        }
}

internal enum class CanvasTool {
    SELECT,
    PEN,
}
