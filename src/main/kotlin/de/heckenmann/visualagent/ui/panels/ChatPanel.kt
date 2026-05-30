package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.AppIdentity
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.ui.FxmlLoader
import javafx.animation.FadeTransition
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.beans.binding.Bindings
import javafx.fxml.FXML
import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.ProgressIndicator
import javafx.scene.control.TextArea
import javafx.scene.control.Tooltip
import javafx.scene.image.ImageView
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
import javafx.util.Duration

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

    @FXML
    private lateinit var clearChatButton: Button

    @FXML
    private lateinit var conversationMetaLabel: Label

    @FXML
    private lateinit var connectionInfoLabel: Label

    @FXML
    private lateinit var modelInfoLabel: Label

    @FXML
    private lateinit var agentsInfoLabel: Label

    @FXML
    private lateinit var assistantBusyContainer: HBox

    @FXML
    private lateinit var assistantBusySpinner: ProgressIndicator

    @FXML
    private lateinit var assistantBusyLabel: Label

    @FXML
    private lateinit var conversationIconImage: ImageView

    private var onSendMessage: ((String) -> Unit)? = null
    private var onClearConversation: (() -> Unit)? = null
    private val LOADING_TOKEN = "__loading__"
    private val waitingForAssistant = SimpleBooleanProperty(false)
    private var typingDots = 0
    private val thinkingPhases = listOf("Analyzing context", "Planning steps", "Generating response")
    private var thinkingStartedAtMillis: Long = 0L
    private val typingTimeline = Timeline(
        KeyFrame(Duration.millis(360.0), {
            if (waitingForAssistant.get()) {
                typingDots = (typingDots + 1) % 4
                val dots = ".".repeat(typingDots)
                val elapsed = ((System.currentTimeMillis() - thinkingStartedAtMillis) / 1000).coerceAtLeast(0)
                val phase = thinkingPhases[(elapsed.toInt() / 2) % thinkingPhases.size]
                conversationMetaLabel.text = "Thinking $dots  $phase  ${elapsed}s"
                assistantBusyLabel.text = "$phase  ${elapsed}s"
            }
        }),
    ).apply {
        cycleCount = Timeline.INDEFINITE
    }

    init {
        styleClass.add("chat-panel")
        val root = FxmlLoader.load(this, "chat-panel.fxml")
        children.add(root)
    }

    @FXML
    private fun initialize() {
        AppIdentity.javaFxIcon()?.let { conversationIconImage.image = it }
        messagesListView.cellFactory = javafx.util.Callback { ChatMessageCell() }
        messagesListView.isFocusTraversable = false
        messagesListView.styleClass.add("chat-message-list-no-hbar")

        sendButton.graphic = FontIcon(FontAwesomeSolid.PAPER_PLANE)
        sendButton.tooltip = Tooltip("Send")
        clearChatButton.graphic = FontIcon(FontAwesomeSolid.BROOM)
        clearChatButton.tooltip = Tooltip("Clear conversation")
        clearChatButton.isFocusTraversable = false

        sendButton.setOnAction { sendMessage() }
        clearChatButton.setOnAction {
            clearMessages()
            onClearConversation?.invoke()
        }
        assistantBusySpinner.maxWidth = 14.0
        assistantBusySpinner.maxHeight = 14.0
        assistantBusyContainer.isManaged = false
        assistantBusyContainer.isVisible = false

        // Disable send when input is empty
        sendButton.disableProperty().bind(inputTextField.textProperty().isEmpty.or(waitingForAssistant))
        updateMeta()
        updateSessionContext(model = "--", connected = false, agentCount = 0)

        // Enter = send, Shift+Enter = newline
        inputTextField.addEventFilter(KeyEvent.KEY_PRESSED) { event ->
            val shortcutEnter = event.code == KeyCode.ENTER && event.isShortcutDown
            if ((event.code == KeyCode.ENTER && !event.isShiftDown) || shortcutEnter) {
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
        if (waitingForAssistant.get()) return
        val text = inputTextField.text.trim()
        if (text.isEmpty()) return

        inputTextField.clear()
        messagesListView.items.add(ChatMessage("user", text))
        scrollToBottom()

        // Show assistant loading placeholder and invoke handler
        messagesListView.items.add(ChatMessage("assistant", LOADING_TOKEN))
        waitingForAssistant.set(true)
        thinkingStartedAtMillis = System.currentTimeMillis()
        typingTimeline.playFromStart()
        updateBusyIndicator(true, thinkingPhases.first())
        updateMeta("Thinking")
        scrollToBottom()
        onSendMessage?.invoke(text)
    }

    fun setOnSendMessage(callback: (String) -> Unit) {
        onSendMessage = callback
    }

    fun setOnClearConversation(callback: () -> Unit) {
        onClearConversation = callback
    }

    fun setConversationHistory(history: List<Message>) {
        messagesListView.items.clear()
        history.forEach { msg ->
            if (msg.content.isNotBlank()) {
                messagesListView.items.add(ChatMessage(msg.role, msg.content))
            }
        }
        waitingForAssistant.set(false)
        typingTimeline.stop()
        updateBusyIndicator(false, "Idle")
        updateMeta()
        scrollToBottom()
    }

    fun addAssistantMessage(text: String) {
        // If there is a loading placeholder at the end, replace it with the real message
        val items = messagesListView.items
        if (items.isNotEmpty()) {
            val last = items[items.size - 1]
            if (last.role == "assistant" && last.content == LOADING_TOKEN) {
                items[items.size - 1] = ChatMessage("assistant", text)
                waitingForAssistant.set(false)
                typingTimeline.stop()
                updateBusyIndicator(false, "Idle")
                updateMeta()
                scrollToBottom()
                return
            }
        }

        items.add(ChatMessage("assistant", text))
        waitingForAssistant.set(false)
        typingTimeline.stop()
        updateBusyIndicator(false, "Idle")
        updateMeta()
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
                waitingForAssistant.set(true)
                thinkingStartedAtMillis = System.currentTimeMillis()
                typingTimeline.playFromStart()
                updateBusyIndicator(true, thinkingPhases.first())
                updateMeta("Thinking")
                scrollToBottom()
                onSendMessage?.invoke(m.content)
                return
            }
        }
    }

    fun clearMessages() {
        messagesListView.items.clear()
        waitingForAssistant.set(false)
        typingTimeline.stop()
        updateBusyIndicator(false, "Idle")
        updateMeta()
    }

    private fun scrollToBottom() {
        val count = messagesListView.items.size
        if (count > 0) {
            messagesListView.scrollTo(count - 1)
        }
    }

    private fun updateMeta(status: String? = null) {
        val count = messagesListView.items.size
        conversationMetaLabel.text = status ?: "$count messages in this session"
    }

    fun updateSessionContext(model: String, connected: Boolean, agentCount: Int) {
        connectionInfoLabel.text = if (connected) "Connected" else "Disconnected"
        connectionInfoLabel.styleClass.removeAll("chat-session-chip-online", "chat-session-chip-offline")
        connectionInfoLabel.styleClass.add(if (connected) "chat-session-chip-online" else "chat-session-chip-offline")
        modelInfoLabel.text = "Model $model"
        agentsInfoLabel.text = "Agents $agentCount"
    }

    fun updateResponseMetrics(durationMillis: Long) {
        val seconds = durationMillis / 1000.0
        val rounded = String.format("%.2fs", seconds)
        Platform.runLater { updateMeta("Last response in $rounded") }
    }

    private fun updateBusyIndicator(active: Boolean, text: String) {
        assistantBusyContainer.isManaged = active
        assistantBusyContainer.isVisible = active
        assistantBusyLabel.text = text
    }

    private inner class ChatMessageCell : ListCell<ChatMessage>() {

        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())

        private val copyButton = Button(null, FontIcon(FontAwesomeSolid.COPY))
        private val copyRawButton = Button(null, FontIcon(FontAwesomeSolid.FILE))
        private val retryButton = Button(null, FontIcon(FontAwesomeSolid.REDO))

        init {
            copyButton.styleClass.add("button-icon")
            copyRawButton.styleClass.add("button-icon")
            retryButton.styleClass.add("button-icon")
            copyButton.tooltip = Tooltip("Copy")
            copyRawButton.tooltip = Tooltip("Copy raw")
            retryButton.tooltip = Tooltip("Retry")
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
                row.maxWidth = Double.MAX_VALUE
                contentArea.maxWidth = Double.MAX_VALUE
                row.prefWidthProperty().bind(Bindings.subtract(widthProperty(), 24.0))
                if (item.content != LOADING_TOKEN) {
                    row.opacity = 0.0
                    val fade = FadeTransition(Duration.millis(180.0), row)
                    fade.fromValue = 0.0
                    fade.toValue = 1.0
                    fade.play()
                }

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

            val avatar = Label(if (item.role == "user") "You" else "AI")
            avatar.styleClass.addAll("chat-avatar", if (item.role == "user") "chat-avatar-user" else "chat-avatar-assistant")
            return avatar
        }

        private fun createContentArea(item: ChatMessage, isGrouped: Boolean): VBox {
            if (isGrouped) {
                val contentBody = createMessageBody(item)

                val actionButtons = HBox(copyButton, copyRawButton)
                actionButtons.styleClass.add("chat-action-buttons")
                copyButton.setOnAction { copyToClipboard(item.content) }
                copyRawButton.setOnAction { copyToClipboard(item.content) }

                val contentArea = VBox(contentBody, actionButtons)
                contentArea.styleClass.add("chat-content-area")
                contentArea.isFillWidth = true
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

            val contentBody = createMessageBody(item)

            val actionButtons = HBox(copyButton, copyRawButton, retryButton)
            actionButtons.styleClass.add("chat-action-buttons")

            val contentArea = VBox(header, contentBody, actionButtons)
            contentArea.styleClass.add("chat-content-area")
            contentArea.isFillWidth = true

            copyButton.setOnAction { copyToClipboard(item.content) }
            copyRawButton.setOnAction { copyToClipboard(item.content) }
            retryButton.setOnAction {
                // Retry by finding the user message before this assistant message
                val idx = index
                this@ChatPanel.retryAssistantAt(idx)
            }

            // Hide retry for non-assistant messages
            retryButton.isVisible = item.role == "assistant"

            return contentArea
        }

        private fun createMessageBody(item: ChatMessage): Region {
            if (item.content == LOADING_TOKEN) {
                val loadingSpinner = ProgressIndicator()
                loadingSpinner.progress = -1.0
                loadingSpinner.maxWidth = 16.0
                loadingSpinner.maxHeight = 16.0
                loadingSpinner.styleClass.add("assistant-loading-spinner")

                val loadingLabel = Label("Main agent is working")
                loadingLabel.styleClass.add("assistant-loading")

                val loadingRow = HBox(loadingSpinner, loadingLabel)
                loadingRow.styleClass.add("assistant-loading-row")
                return loadingRow
            }

            return ChatMarkdownRenderer.render(item.content)
        }

        private fun copyToClipboard(text: String) {
            val clipboard = Clipboard.getSystemClipboard()
            val content = ClipboardContent()
            content.putString(text)
            clipboard.setContent(content)
        }
    }
}
