package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.ui.FxmlLoader
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.TextArea
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid
import org.kordamp.ikonli.javafx.FontIcon
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ChatMessage(
    val role: String,
    val content: String,
    val timestamp: Instant = Instant.now(),
)

class ChatPanel : Region() {

    @FXML
    private lateinit var rootBorderPane: BorderPane

    @FXML
    private lateinit var messagesListView: ListView<ChatMessage>

    @FXML
    private lateinit var inputTextField: TextArea

    @FXML
    private lateinit var sendButton: Button

    private var onSendMessage: ((String) -> Unit)? = null
    private val LOADING_TOKEN = "__loading__"

    init {
        styleClass.add("chat-panel")
        val root = FxmlLoader.load(this, "chat-panel.fxml")
        children.add(root)
    }

    @FXML
    private fun initialize() {
        messagesListView.cellFactory = javafx.util.Callback { ChatMessageCell() }

        sendButton.setOnAction { sendMessage() }

        // Disable send when input is empty
        sendButton.disableProperty().bind(inputTextField.textProperty().isEmpty)

        // Enter = send, Shift+Enter = newline
        inputTextField.addEventFilter(KeyEvent.KEY_PRESSED) { event ->
            if (event.code == KeyCode.ENTER && !event.isShiftDown) {
                sendMessage()
                event.consume()
            }
        }

        // Slash (/) focuses the input for quick search-like behaviour
        rootBorderPane.sceneProperty().addListener { _, _, newScene ->
            newScene?.addEventFilter(KeyEvent.KEY_PRESSED) { ev ->
                if (ev.text == "/" && ev.code == KeyCode.SLASH) {
                    inputTextField.requestFocus()
                    ev.consume()
                }
            }
        }

        VBox.setVgrow(this, Priority.ALWAYS)
        BorderPane.setAlignment(rootBorderPane, javafx.geometry.Pos.CENTER)

        widthProperty().addListener { _, _, newW ->
            rootBorderPane.prefWidth = newW.toDouble() - 1
        }
        heightProperty().addListener { _, _, newH ->
            rootBorderPane.prefHeight = newH.toDouble() - 1
        }
    }

    override fun computeMinWidth(height: Double): Double = 400.0
    override fun computeMinHeight(width: Double): Double = 300.0
    override fun computePrefWidth(height: Double): Double = 800.0
    override fun computePrefHeight(width: Double): Double = 600.0

    override fun layoutChildren() {
        super.layoutChildren()
        rootBorderPane.layoutX = 0.0
        rootBorderPane.layoutY = 0.0
        rootBorderPane.prefWidth(width)
        rootBorderPane.prefHeight(height)
        rootBorderPane.resize(width, height)
    }

    private fun sendMessage() {
        val text = inputTextField.text.trim()
        if (text.isEmpty()) return

        inputTextField.clear()
        messagesListView.items.add(ChatMessage("user", text))
        scrollToBottom()

        // Show assistant loading placeholder and invoke handler
        messagesListView.items.add(ChatMessage("assistant", LOADING_TOKEN))
        scrollToBottom()
        onSendMessage?.invoke(text)
    }

    fun setOnSendMessage(callback: (String) -> Unit) {
        onSendMessage = callback
    }

    fun addAssistantMessage(text: String) {
        // If there is a loading placeholder at the end, replace it with the real message
        val items = messagesListView.items
        if (items.isNotEmpty()) {
            val last = items[items.size - 1]
            if (last.role == "assistant" && last.content == LOADING_TOKEN) {
                items[items.size - 1] = ChatMessage("assistant", text)
                scrollToBottom()
                return
            }
        }

        items.add(ChatMessage("assistant", text))
        scrollToBottom()
    }

    /**
     * Retry sending the user message that precedes the assistant message at [assistantIndex].
     */
    fun retryAssistantAt(assistantIndex: Int) {
        val items = messagesListView.items
        if (assistantIndex < 0 || assistantIndex >= items.size) return

        // find the previous user message
        for (i in assistantIndex - 1 downTo 0) {
            val m = items[i]
            if (m.role == "user") {
                // set assistant slot to loading and resend
                items[assistantIndex] = ChatMessage("assistant", LOADING_TOKEN)
                scrollToBottom()
                onSendMessage?.invoke(m.content)
                return
            }
        }
    }

    fun clearMessages() {
        messagesListView.items.clear()
    }

    private fun scrollToBottom() {
        val count = messagesListView.items.size
        if (count > 0) {
            messagesListView.scrollTo(count - 1)
        }
    }

    private inner class ChatMessageCell : ListCell<ChatMessage>() {

        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())

        private val copyButton = Button(null, FontIcon(FontAwesomeSolid.COPY))
        private val copyRawButton = Button(null, FontIcon(FontAwesomeSolid.FILE))
        private val retryButton = Button("Retry")

        init {
            copyButton.styleClass.add("button-icon")
            copyRawButton.styleClass.add("button-icon")
            retryButton.styleClass.add("button-icon")
            copyButton.tooltip = javafx.scene.control.Tooltip("Copy")
            copyRawButton.tooltip = javafx.scene.control.Tooltip("Copy raw")
            retryButton.tooltip = javafx.scene.control.Tooltip("Retry sending the previous user message")
            copyButton.isFocusTraversable = false
            copyRawButton.isFocusTraversable = false
            retryButton.isFocusTraversable = false
        }

        override fun updateItem(item: ChatMessage?, empty: Boolean) {
            super.updateItem(item, empty)
            if (empty || item == null) {
                graphic = null
                styleClass.removeAll("chat-row", "chat-row-user", "chat-row-assistant", "chat-row-grouped")
                text = null
            } else {
                text = null
                styleClass.removeAll("chat-row", "chat-row-user", "chat-row-assistant", "chat-row-grouped")
                styleClass.add("chat-row")

                val isGrouped = isSameRoleAsPrevious()

                if (isGrouped) {
                    styleClass.add("chat-row-grouped")
                }

                val avatarSlot = createAvatarSlot(item, isGrouped)
                val contentArea = createContentArea(item, isGrouped)

                val row = HBox(avatarSlot, contentArea)
                row.styleClass.add("chat-row-inner")
                HBox.setHgrow(contentArea, Priority.ALWAYS)

                if (item.role == "user") {
                    styleClass.add("chat-row-user")
                } else {
                    styleClass.add("chat-row-assistant")
                }

                graphic = row
            }
        }

        private fun isSameRoleAsPrevious(): Boolean {
            val idx = index
            if (idx <= 0) return false
            val listView = listView ?: return false
            val items = listView.items
            if (idx >= items.size) return false
            val current = items[idx] ?: return false
            val previous = items[idx - 1] ?: return false
            return current.role == previous.role
        }

        private fun createAvatarSlot(item: ChatMessage, isGrouped: Boolean): Region {
            if (isGrouped) {
                val spacer = Region()
                spacer.styleClass.add("chat-avatar-spacer")
                return spacer
            }

            val avatar = Label(if (item.role == "user") "U" else "A")
            avatar.styleClass.addAll("chat-avatar", if (item.role == "user") "chat-avatar-user" else "chat-avatar-assistant")
            return avatar
        }

        private fun createContentArea(item: ChatMessage, isGrouped: Boolean): VBox {
            if (isGrouped) {
                val contentLabel = Label(item.content)
                contentLabel.isWrapText = true
                contentLabel.maxWidth = Double.MAX_VALUE
                contentLabel.styleClass.add("chat-message-content")

                val actionButtons = HBox(copyButton, copyRawButton)
                actionButtons.styleClass.add("chat-action-buttons")

                val contentArea = VBox(contentLabel, actionButtons)
                contentArea.styleClass.add("chat-content-area")
                return contentArea
            }

            val roleName = Label(if (item.role == "user") "You" else "Assistant")
            roleName.styleClass.add("chat-role-label")
            if (item.role == "user") {
                roleName.styleClass.add("chat-role-label-user")
            } else {
                roleName.styleClass.add("chat-role-label-assistant")
            }

            val timeText = timeFormatter.format(item.timestamp)
            val timeLabel = Label(timeText)
            timeLabel.styleClass.add("chat-time-label")

            val header = HBox(roleName, timeLabel)
            header.styleClass.add("chat-message-header")

            val contentLabel = Label(item.content)
            contentLabel.isWrapText = true
            contentLabel.maxWidth = Double.MAX_VALUE
            contentLabel.styleClass.add("chat-message-content")

                val actionButtons = HBox(copyButton, copyRawButton, retryButton)
                actionButtons.styleClass.add("chat-action-buttons")

            val contentArea = VBox(header, contentLabel, actionButtons)
            contentArea.styleClass.add("chat-content-area")

            copyButton.setOnAction { copyToClipboard(item.content) }
            copyRawButton.setOnAction { copyToClipboard(item.content) }
            retryButton.setOnAction {
                // Retry by finding the user message before this assistant message
                val idx = index
                this@ChatPanel.retryAssistantAt(idx)
            }

            // Hide retry for non-assistant messages
            retryButton.isVisible = item.role == "assistant"

            // Style loading state
            if (item.content == LOADING_TOKEN) {
                contentLabel.text = "…"
                contentLabel.styleClass.add("assistant-loading")
                retryButton.isVisible = false
            }

            return contentArea
        }

        private fun copyToClipboard(text: String) {
            val clipboard = Clipboard.getSystemClipboard()
            val content = ClipboardContent()
            content.putString(text)
            clipboard.setContent(content)
        }
    }
}
