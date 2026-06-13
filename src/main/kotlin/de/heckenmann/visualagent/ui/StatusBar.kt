package de.heckenmann.visualagent.ui

import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Region

/**
 * Represents StatusBar.
 */
class StatusBar : Region() {
    companion object {
        const val HEIGHT = 40.0
    }

    private val rootBorderPane = BorderPane()
    private val agentsLabel = Label("Agents: 0/0")
    private val reconnectButton = Button("Reconnect")
    private val retryButton = Button("Retry")
    private var onReconnect: (() -> Unit)? = null

    init {
        setupUI()
    }

    private fun setupUI() {
        rootBorderPane.styleClass.add("status-bar")

        reconnectButton.isFocusTraversable = false
        retryButton.isFocusTraversable = false
        reconnectButton.setOnAction { onReconnect?.invoke() }
        retryButton.setOnAction { onReconnect?.invoke() }
        reconnectButton.styleClass.addAll("status-action-button")
        retryButton.styleClass.addAll("status-action-button", "status-action-secondary")
        agentsLabel.styleClass.add("status-bar-muted")

        val agentsChip =
            HBox(agentsLabel).apply {
                styleClass.add("status-chip")
                alignment = Pos.CENTER_LEFT
            }

        val rightGroup =
            HBox(agentsChip, retryButton, reconnectButton).apply {
                styleClass.add("status-group")
                alignment = Pos.CENTER_RIGHT
                spacing = 8.0
            }

        rootBorderPane.left = null
        rootBorderPane.right = rightGroup
        children.add(rootBorderPane)

        minHeight = HEIGHT
        prefHeight = HEIGHT
        maxHeight = HEIGHT
    }

    /**
     * Executes updateAgentCount.
     */
    fun updateAgentCount(
        active: Int,
        total: Int,
    ) {
        agentsLabel.text = "Agents: $active/$total"
    }

    /**
     * Executes setOnReconnect.
     */
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
