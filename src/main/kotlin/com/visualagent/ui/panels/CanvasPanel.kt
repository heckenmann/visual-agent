package com.visualagent.ui.panels

import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.layout.BorderPane
import javafx.scene.control.Label
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight

class CanvasPanel : Region() {
    
    private val rootBorderPane = BorderPane()
    private val canvas = Canvas(800.0, 300.0)
    private val gc: GraphicsContext = canvas.graphicsContext2D
    
    init {
        setupUI()
        clearCanvas()
    }
    
    private fun setupUI() {
        styleClass.add("canvas-panel")
        style = "-fx-background-color: #1e1e1e;"
        
        val titleLabel = Label("Canvas")
        titleLabel.font = Font.font("System", FontWeight.BOLD, 14.0)
        titleLabel.style = "-fx-text-fill: #e0e0e0; -fx-padding: 4px;"
        
        rootBorderPane.top = titleLabel
        rootBorderPane.center = canvas
        
        canvas.widthProperty().bind(widthProperty().subtract(20.0))
        canvas.heightProperty().bind(heightProperty().subtract(40.0))
        
        children.add(rootBorderPane)
        BorderPane.setMargin(canvas, javafx.geometry.Insets(8.0))
        VBox.setVgrow(this, Priority.ALWAYS)
    }
    
    fun clearCanvas() {
        gc.fill = Color.web("#1e1e1e")
        gc.fillRect(0.0, 0.0, canvas.width, canvas.height)
    }
    
    fun drawText(text: String, x: Double, y: Double, color: String = "#e0e0e0") {
        gc.fill = Color.web(color)
        gc.font = Font.font("System", 14.0)
        gc.fillText(text, x, y)
    }
    
    fun drawRect(x: Double, y: Double, width: Double, height: Double, fillColor: String, strokeColor: String? = null) {
        gc.fill = Color.web(fillColor)
        gc.fillRect(x, y, width, height)
        if (strokeColor != null) {
            gc.stroke = Color.web(strokeColor)
            gc.strokeRect(x, y, width, height)
        }
    }
    
    fun drawLine(x1: Double, y1: Double, x2: Double, y2: Double, color: String = "#4caf50", width: Double = 2.0) {
        gc.stroke = Color.web(color)
        gc.lineWidth = width
        gc.beginPath()
        gc.moveTo(x1, y1)
        gc.lineTo(x2, y2)
        gc.stroke()
    }
    
    fun drawCircle(centerX: Double, centerY: Double, radius: Double, fillColor: String) {
        gc.fill = Color.web(fillColor)
        gc.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2)
    }
}
