package de.heckenmann.visualagent.ui

import javafx.scene.control.Label
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region

class StatusBar : Region() {

    private val rootBorderPane = BorderPane()
    private val connectionLabel = Label("Ollama: Disconnected")
    private val modelLabel = Label("Model: --")
    private val agentsLabel = Label("Agents: 0/0")

    init {
        setupUI()
    }

    private fun setupUI() {
        styleClass.add("status-bar")

        connectionLabel.styleClass.addAll("status-bar-muted", "status-bar-warning")

        modelLabel.styleClass.add("status-bar-muted")

        agentsLabel.styleClass.add("status-bar-muted")

        val spacer = Region()
        HBox.setHgrow(spacer, Priority.ALWAYS)

        val statusBox = HBox(connectionLabel, modelLabel, spacer, agentsLabel)
        statusBox.spacing = 20.0

        rootBorderPane.center = statusBox

        children.add(rootBorderPane)
        prefHeight = 32.0
    }

    fun updateConnectionStatus(connected: Boolean) {
        connectionLabel.text = if (connected) "Ollama: Connected" else "Ollama: Disconnected"
        connectionLabel.styleClass.removeAll("status-bar-connected", "status-bar-disconnected", "status-bar-warning")
        if (connected) {
            connectionLabel.styleClass.add("status-bar-connected")
        } else {
            connectionLabel.styleClass.add("status-bar-disconnected")
        }
    }

    fun updateModel(model: String) {
        modelLabel.text = "Model: $model"
    }

    fun updateAgentCount(active: Int, total: Int) {
        agentsLabel.text = "Agents: $active/$total"
    }
}
