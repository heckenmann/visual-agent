package de.heckenmann.visualagent.ui

import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Region

/**
 * Fixed-height footer that shows workspace persistence and agent activity.
 */
class StatusBar : Region() {
    companion object {
        const val HEIGHT = 34.0
    }

    private val rootBorderPane = BorderPane()
    private val agentsLabel = Label("0 of 0 agents active")

    init {
        setupUI()
    }

    private fun setupUI() {
        rootBorderPane.styleClass.add("status-bar")

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
            HBox(agentsChip).apply {
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

    override fun computeMinHeight(width: Double): Double = HEIGHT

    override fun computePrefHeight(width: Double): Double = HEIGHT

    override fun computeMaxHeight(width: Double): Double = HEIGHT

    override fun layoutChildren() {
        rootBorderPane.resizeRelocate(0.0, 0.0, width, HEIGHT)
    }
}
