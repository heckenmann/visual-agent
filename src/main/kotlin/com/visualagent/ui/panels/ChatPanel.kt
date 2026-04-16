package com.visualagent.ui.panels

import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.scene.text.FontWeight

class ChatPanel : Region() {

    private val rootBorderPane = BorderPane()
    private val messagesListView = ListView<String>()
    private val inputTextField = TextField()
    private val sendButton = Button("Send")

    init {
        setupUI()
    }

    private fun setupUI() {
        styleClass.add("chat-panel")
        style = "-fx-background-color: #2d2d2d;"

        val titleLabel = Label("Chat")
        titleLabel.font = Font.font("System", FontWeight.BOLD, 16.0)
        titleLabel.style = "-fx-text-fill: #e0e0e0; -fx-padding: 8px;"

        val header = Region()
        header.style = "-fx-background-color: #2d2d2d;"
        header.prefHeight = 40.0
        rootBorderPane.top = header

        messagesListView.style = "-fx-background: #1e1e1e; -fx-text-fill: #e0e0e0;"
        messagesListView.items.add("Welcome to Visual Agent! How can I help you today?")

        inputTextField.promptText = "Type your message..."
        inputTextField.style = "-fx-background-color: #3d3d3d; -fx-text-fill: #ffffff; -fx-prompt-text-fill: #808080;"
        inputTextField.prefColumnCount = 40

        sendButton.style = "-fx-background-color: #4caf50; -fx-text-fill: #ffffff;"

        val inputBox = VBox(inputTextField, sendButton)
        inputBox.spacing = 8.0
        inputBox.style = "-fx-background-color: #2d2d2d; -fx-padding: 8px;"

        rootBorderPane.center = messagesListView
        rootBorderPane.bottom = inputBox

        children.add(rootBorderPane)
        VBox.setVgrow(messagesListView, Priority.ALWAYS)
    }

    fun addMessage(message: String, isUser: Boolean = false) {
        val prefix = if (isUser) "You: " else "Agent: "
        messagesListView.items.add(prefix + message)
    }

    fun clearMessages() {
        messagesListView.items.clear()
    }
}
