package de.heckenmann.visualagent.ui.panels

import javafx.embed.swing.SwingFXUtils
import javafx.geometry.Insets
import javafx.geometry.Point2D
import javafx.scene.SnapshotParameters
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.image.WritableImage
import javafx.scene.input.MouseEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import javafx.scene.text.Font
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.io.File
import javax.imageio.ImageIO

/**
 * Represents CanvasPanel.
 */
@Component
@Lazy
class CanvasPanel : Region() {
    private val rootBorderPane = BorderPane()
    private val toolbar = HBox(8.0)
    private val canvas = Canvas()
    private val gc: GraphicsContext = canvas.graphicsContext2D
    private val titleLabel = Label("Canvas")
    private val undoStack = ArrayDeque<WritableImage>()
    private val redoStack = ArrayDeque<WritableImage>()
    private var activeTool: CanvasTool = CanvasTool.PEN
    private var lastPoint: Point2D? = null
    private var zoom = 1.0
    private var gridEnabled = true
    private var panning = false
    private var panStart = Point2D.ZERO

    init {
        setupUI()
        clearCanvas()
    }

    private fun setupUI() {
        styleClass.add("canvas-panel")

        titleLabel.styleClass.add("panel-title-label")
        toolbar.styleClass.add("canvas-toolbar")
        toolbar.padding = Insets(8.0)

        val penButton = Button("Pen")
        val eraserButton = Button("Eraser")
        val undoButton = Button("Undo")
        val redoButton = Button("Redo")
        val zoomOutButton = Button("-")
        val zoomResetButton = Button("100%")
        val zoomInButton = Button("+")
        val gridToggle = CheckBox("Grid")
        val clearButton = Button("Clear")
        val exportButton = Button("Export PNG")

        penButton.setOnAction { activeTool = CanvasTool.PEN }
        eraserButton.setOnAction { activeTool = CanvasTool.ERASER }
        undoButton.setOnAction { undo() }
        redoButton.setOnAction { redo() }
        zoomOutButton.setOnAction { setZoom(zoom - 0.1) }
        zoomResetButton.setOnAction { setZoom(1.0) }
        zoomInButton.setOnAction { setZoom(zoom + 0.1) }
        gridToggle.isSelected = true
        gridToggle.setOnAction {
            gridEnabled = gridToggle.isSelected
            clearCanvas()
        }
        clearButton.setOnAction { clearCanvas() }
        exportButton.setOnAction { exportPng() }

        toolbar.children.addAll(
            titleLabel,
            penButton,
            eraserButton,
            undoButton,
            redoButton,
            zoomOutButton,
            zoomResetButton,
            zoomInButton,
            gridToggle,
            clearButton,
            exportButton,
        )

        rootBorderPane.top = toolbar
        rootBorderPane.center = canvas

        canvas.widthProperty().bind(widthProperty().subtract(20.0))
        canvas.heightProperty().bind(heightProperty().subtract(40.0))

        children.add(rootBorderPane)
        BorderPane.setMargin(canvas, Insets(8.0))

        canvas.setOnMousePressed { event ->
            if (event.isSecondaryButtonDown || event.isMiddleButtonDown) {
                panning = true
                panStart = Point2D(event.sceneX - canvas.translateX, event.sceneY - canvas.translateY)
            } else {
                recordState()
                redoStack.clear()
                lastPoint = Point2D(event.x, event.y)
            }
        }
        canvas.setOnMouseDragged { event ->
            if (panning) {
                canvas.translateX = event.sceneX - panStart.x
                canvas.translateY = event.sceneY - panStart.y
            } else {
                val prev = lastPoint ?: Point2D(event.x, event.y)
                val current = Point2D(event.x, event.y)
                when (activeTool) {
                    CanvasTool.PEN -> drawStroke(prev, current, "#4caf50", 2.0)
                    CanvasTool.ERASER -> drawStroke(prev, current, "#1e1e1e", 10.0)
                }
                lastPoint = current
            }
        }
        canvas.addEventHandler(MouseEvent.MOUSE_RELEASED) {
            lastPoint = null
            panning = false
        }

        minHeight = 100.0
        prefHeight = 200.0
        maxHeight = 400.0
    }

    /**
     * Executes clearCanvas.
     */
    fun clearCanvas() {
        recordState()
        gc.fill = Color.web("#1e1e1e")
        gc.fillRect(0.0, 0.0, canvas.width, canvas.height)
        if (gridEnabled) drawGrid()
    }

    /**
     * Executes drawText.
     */
    fun drawText(
        text: String,
        x: Double,
        y: Double,
        color: String = "#e0e0e0",
    ) {
        recordState()
        gc.fill = Color.web(color)
        gc.font = Font.font("System", 14.0)
        gc.fillText(text, x, y)
    }

    /**
     * Executes drawRect.
     */
    fun drawRect(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        fillColor: String,
        strokeColor: String? = null,
    ) {
        recordState()
        gc.fill = Color.web(fillColor)
        gc.fillRect(x, y, width, height)
        if (strokeColor != null) {
            gc.stroke = Color.web(strokeColor)
            gc.strokeRect(x, y, width, height)
        }
    }

    /**
     * Executes drawLine.
     */
    fun drawLine(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        color: String = "#4caf50",
        width: Double = 2.0,
    ) {
        recordState()
        gc.stroke = Color.web(color)
        gc.lineWidth = width
        gc.beginPath()
        gc.moveTo(x1, y1)
        gc.lineTo(x2, y2)
        gc.stroke()
    }

    /**
     * Executes drawCircle.
     */
    fun drawCircle(
        centerX: Double,
        centerY: Double,
        radius: Double,
        fillColor: String,
    ) {
        recordState()
        gc.fill = Color.web(fillColor)
        gc.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2)
    }

    private fun drawStroke(
        from: Point2D,
        to: Point2D,
        color: String,
        lineWidth: Double,
    ) {
        gc.stroke = Color.web(color)
        gc.lineWidth = lineWidth
        gc.beginPath()
        gc.moveTo(from.x, from.y)
        gc.lineTo(to.x, to.y)
        gc.stroke()
    }

    private fun recordState() {
        val snapshot = WritableImage(canvas.width.toInt().coerceAtLeast(1), canvas.height.toInt().coerceAtLeast(1))
        canvas.snapshot(SnapshotParameters(), snapshot)
        undoStack.addLast(snapshot)
        if (undoStack.size > 20) undoStack.removeFirst()
    }

    private fun restore(snapshot: WritableImage) {
        gc.clearRect(0.0, 0.0, canvas.width, canvas.height)
        gc.drawImage(snapshot, 0.0, 0.0)
    }

    private fun setZoom(newZoom: Double) {
        zoom = newZoom.coerceIn(0.5, 2.0)
        canvas.scaleX = zoom
        canvas.scaleY = zoom
    }

    private fun drawGrid() {
        val spacing = 25.0
        gc.stroke = Color.web("#2c2c2c")
        gc.lineWidth = 1.0
        var x = 0.0
        while (x < canvas.width) {
            gc.strokeLine(x, 0.0, x, canvas.height)
            x += spacing
        }
        var y = 0.0
        while (y < canvas.height) {
            gc.strokeLine(0.0, y, canvas.width, y)
            y += spacing
        }
    }

    private fun undo() {
        if (undoStack.isEmpty()) return
        val current = WritableImage(canvas.width.toInt().coerceAtLeast(1), canvas.height.toInt().coerceAtLeast(1))
        canvas.snapshot(SnapshotParameters(), current)
        redoStack.addLast(current)
        restore(undoStack.removeLast())
    }

    private fun redo() {
        if (redoStack.isEmpty()) return
        val current = WritableImage(canvas.width.toInt().coerceAtLeast(1), canvas.height.toInt().coerceAtLeast(1))
        canvas.snapshot(SnapshotParameters(), current)
        undoStack.addLast(current)
        restore(redoStack.removeLast())
    }

    private fun exportPng() {
        val file = File("data/canvas-export.png")
        file.parentFile?.mkdirs()
        val snapshot = canvas.snapshot(SnapshotParameters(), null)
        ImageIO.write(SwingFXUtils.fromFXImage(snapshot, null), "png", file)
    }

    private enum class CanvasTool { PEN, ERASER }
}
