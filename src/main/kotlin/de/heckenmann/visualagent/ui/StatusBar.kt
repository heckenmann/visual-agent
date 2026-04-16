package de.heckenmann.visualagent.ui

import javafx.scene.control.Label
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.text.Font
import javafx.scene.text.FontWeight

class StatusBar : Region() {

    private val rootBorderPane = BorderPane()
    private val connectionLabel = Label("Ollama: Disconnected")
    private val modelLabel = Label("Model: --")
    private val agentsLabel = Label("Agents: 0/0")

    init {
        setupUI()
    }

    private fun setupUI() {
        style = "-fx-background-color: #1a1a1a; -fx-padding: 4px;"

        connectionLabel.font = Font.font("System", FontWeight.NORMAL, 12.0)
        connectionLabel.style = "-fx-text-fill: #ff9800;"

        modelLabel.font = Font.font("System", FontWeight.NORMAL, 12.0)
        modelLabel.style = "-fx-text-fill: #a0a0a0;"

        agentsLabel.font = Font.font("System", FontWeight.NORMAL, 12.0)
        agentsLabel.style = "-fx-text-fill: #a0a0a0;"

        val spacer = Region()
        HBox.setHgrow(spacer, Priority.ALWAYS)

        val statusBox = HBox(connectionLabel, modelLabel, spacer, agentsLabel)
        statusBox.style = "-fx-padding: 4px;"
        statusBox.spacing = 20.0

        rootBorderPane.center = statusBox

        children.add(rootBorderPane)
        prefHeight = 32.0
    }

    fun updateConnectionStatus(connected: Boolean) {
        connectionLabel.text = if (connected) "Ollama: Connected" else "Ollama: Disconnected"
        connectionLabel.style = if (connected) {
            "-fx-text-fill: #4caf50;"
        } else {
            "-fx-text-fill: #f44336;"
        }
    }

    fun updateModel(model: String) {
        modelLabel.text = "Model: $model"
    }

    fun updateAgentCount(active: Int, total: Int) {
        agentsLabel.text = "Agents: $active/$total"
    }
}
