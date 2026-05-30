package de.heckenmann.visualagent.ui

import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region

class StatusBar : Region() {
    companion object {
        const val HEIGHT = 32.0
    }

    private val rootBorderPane = BorderPane()
    private val connectionLabel = Label("Ollama: Disconnected")
    private val modelLabel = Label("Model: --")
    private val agentsLabel = Label("Agents: 0/0")
    private val reconnectButton = Button("Reconnect")
    private val retryButton = Button("Retry")
    private var onReconnect: (() -> Unit)? = null

    init {
        setupUI()
    }

    private fun setupUI() {
        reconnectButton.isFocusTraversable = false
        retryButton.isFocusTraversable = false
        reconnectButton.setOnAction { onReconnect?.invoke() }
        retryButton.setOnAction { onReconnect?.invoke() }

        val spacer = Region()
        HBox.setHgrow(spacer, Priority.ALWAYS)

        val statusBox = HBox(connectionLabel, modelLabel, spacer, retryButton, reconnectButton, agentsLabel)
        statusBox.spacing = 12.0
        statusBox.isFillHeight = true

        rootBorderPane.center = statusBox
        children.add(rootBorderPane)

        minHeight = HEIGHT
        prefHeight = HEIGHT
        maxHeight = HEIGHT
    }

    fun updateConnectionStatus(connected: Boolean) {
        connectionLabel.text = if (connected) "Ollama: Connected" else "Ollama: Disconnected"
        reconnectButton.isDisable = connected
        retryButton.isDisable = connected
    }

    fun updateModel(model: String) {
        modelLabel.text = "Model: $model"
    }

    fun updateAgentCount(active: Int, total: Int) {
        agentsLabel.text = "Agents: $active/$total"
    }

    fun setOnReconnect(callback: () -> Unit) {
        onReconnect = callback
    }

    override fun computeMinHeight(width: Double): Double = HEIGHT
    override fun computePrefHeight(width: Double): Double = HEIGHT
    override fun computeMaxHeight(width: Double): Double = HEIGHT

    override fun layoutChildren() {
        rootBorderPane.resizeRelocate(0.0, 0.0, width, HEIGHT)
    }
}

