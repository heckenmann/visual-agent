package de.heckenmann.visualagent.ui

import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Region

/**
 * Fixed-height footer that shows workspace persistence, agent activity, and reconnect controls.
 */
class StatusBar : Region() {
    companion object {
        const val HEIGHT = 34.0
    }

    private val rootBorderPane = BorderPane()
    private val agentsLabel = Label("0 of 0 agents active")
    private val reconnectButton = Button("Reconnect")
    private var onReconnect: (() -> Unit)? = null

    init {
        setupUI()
    }

    private fun setupUI() {
        rootBorderPane.styleClass.add("status-bar")

        reconnectButton.isFocusTraversable = false
        reconnectButton.setOnAction { onReconnect?.invoke() }
        reconnectButton.styleClass.addAll("status-action-button")
        agentsLabel.styleClass.add("status-bar-muted")
        val workspaceLabel =
            Label("Workspace changes are saved automatically").apply {
                styleClass.add("status-bar-muted")
            }

        val agentsChip =
            HBox(agentsLabel).apply {
                styleClass.add("status-chip")
                alignment = Pos.CENTER_LEFT
            }

        val rightGroup =
            HBox(agentsChip, reconnectButton).apply {
                styleClass.add("status-group")
                alignment = Pos.CENTER_RIGHT
                spacing = 8.0
            }

        rootBorderPane.left = workspaceLabel
        rootBorderPane.right = rightGroup
        children.add(rootBorderPane)

        minHeight = HEIGHT
        prefHeight = HEIGHT
        maxHeight = HEIGHT
    }

    /**
     * Updates the active-agent summary shown in the footer.
     *
     * @param active Number of agents currently running work
     * @param total Total number of configured agents
     */
    fun updateAgentCount(
        active: Int,
        total: Int,
    ) {
        agentsLabel.text = "$active of $total agents active"
    }

    /**
     * Registers the action invoked when the user clicks the reconnect button.
     *
     * @param callback Reconnection callback owned by the main window
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
