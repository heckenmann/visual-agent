package com.visualagent.ui

import com.visualagent.ui.panels.CanvasPanel
import com.visualagent.ui.panels.ChatPanel
import com.visualagent.ui.panels.SubAgentsPanel
import com.visualagent.ui.panels.TodoPanel
import javafx.geometry.Insets
import javafx.scene.layout.BorderPane
import javafx.stage.Stage

class MainWindow : Stage() {

    private val rootPane = BorderPane()
    private val subAgentsPanel = SubAgentsPanel()
    private val chatPanel = ChatPanel()
    private val todoPanel = TodoPanel()
    private val canvasPanel = CanvasPanel()
    private val statusBar = StatusBar()

    init {
        title = "Visual Agent"
        minWidth = 1200.0
        minHeight = 800.0

        setupLayout()
        setupStyles()
    }

    private fun setupLayout() {
        rootPane.top = statusBar
        rootPane.left = subAgentsPanel
        rootPane.center = chatPanel
        rootPane.right = todoPanel
        rootPane.bottom = canvasPanel

        BorderPane.setMargin(subAgentsPanel, Insets(4.0))
        BorderPane.setMargin(chatPanel, Insets(4.0))
        BorderPane.setMargin(todoPanel, Insets(4.0))
        BorderPane.setMargin(canvasPanel, Insets(4.0))

        scene = javafx.scene.Scene(rootPane, 1400.0, 900.0)
    }

    private fun setupStyles() {
        scene.stylesheets.add(
            java.net.URL("file://${System.getProperty("user.dir")}/src/main/resources/styles/dark.css").toExternalForm(),
        )
    }
}
