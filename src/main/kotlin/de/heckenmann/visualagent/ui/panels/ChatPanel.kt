package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.AppIdentity
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.ui.FxmlLoader
import javafx.animation.FadeTransition
import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.ListChangeListener
import javafx.fxml.FXML
import javafx.geometry.Orientation
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.ProgressIndicator
import javafx.scene.control.ScrollBar
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
import javafx.util.Duration
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid
import org.kordamp.ikonli.javafx.FontIcon
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ChatMessage(
    val role: String,
    val content: String,
    val timestamp: Instant = Instant.now(),
    val isToolEvent: Boolean = false,
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
    private lateinit var todoInfoLabel: Label

    @FXML
    private lateinit var openTodosButton: Button

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
    private var onOpenTodos: (() -> Unit)? = null
    private var onLoadOlderMessages: (() -> Unit)? = null
    private var suppressAutoScroll = false
    private var loadingOlderMessages = false
    private var activeToolCalls = 0
    private var latestToolId: String? = null
    private val todoSummaryTooltip = Tooltip()
    private val loadingToken = "__loading__"
    private val waitingForAssistant = SimpleBooleanProperty(false)

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
        messagesListView.items.addListener(
            ListChangeListener { change ->
                if (suppressAutoScroll) return@ListChangeListener
                var hasAddedItems = false
                while (change.next()) {
                    if (change.wasAdded() && change.addedSize > 0) {
                        hasAddedItems = true
                    }
                }
                if (hasAddedItems) {
                    scrollToBottom()
                }
            },
        )

        sendButton.graphic = FontIcon(FontAwesomeSolid.PAPER_PLANE)
        sendButton.tooltip = Tooltip("Send")
        clearChatButton.graphic = FontIcon(FontAwesomeSolid.BROOM)
        clearChatButton.tooltip = Tooltip("Clear conversation")
        clearChatButton.isFocusTraversable = false
        openTodosButton.tooltip = Tooltip("Open todo list")
        openTodosButton.isFocusTraversable = false
        Tooltip.install(todoInfoLabel, todoSummaryTooltip)

        sendButton.setOnAction { sendMessage() }
        openTodosButton.setOnAction { onOpenTodos?.invoke() }
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
        updateTodoSummary(total = 0, open = 0, inProgress = 0, completed = 0, cancelled = 0)

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
        messagesListView.skinProperty().addListener { _, _, _ ->
            installTopScrollListener()
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
        messagesListView.items.add(ChatMessage("assistant", loadingToken))
        waitingForAssistant.set(true)
        updateRuntimeStatus()
        updateMeta()
        scrollToBottom()
        onSendMessage?.invoke(text)
    }

    fun setOnSendMessage(callback: (String) -> Unit) {
        onSendMessage = callback
    }

    fun setOnClearConversation(callback: () -> Unit) {
        onClearConversation = callback
    }

    /**
     * Registers a callback that opens the dedicated todos panel from the conversation header.
     *
     * @param callback Action invoked when the user presses the todos button
     */
    fun setOnOpenTodos(callback: () -> Unit) {
        onOpenTodos = callback
    }

    /**
     * Registers a callback that loads older history entries when the conversation reaches the top.
     *
     * @param callback Action invoked for lazy history pagination
     */
    fun setOnLoadOlderMessages(callback: () -> Unit) {
        onLoadOlderMessages = callback
    }

    fun setConversationHistory(history: List<Message>) {
        suppressAutoScroll = true
        messagesListView.items.clear()
        history.forEach { msg ->
            if (msg.content.isNotBlank()) {
                messagesListView.items.add(
                    ChatMessage(
                        role = msg.role,
                        content = msg.content,
                        isToolEvent = isToolHistoryEntry(msg),
                    ),
                )
            }
        }
        suppressAutoScroll = false
        waitingForAssistant.set(false)
        updateRuntimeStatus()
        updateMeta()
        scrollToBottom()
    }

    /**
     * Prepends older messages without forcing scroll-to-bottom.
     *
     * @param history Older messages in chronological order
     */
    fun prependConversationHistory(history: List<Message>) {
        if (history.isEmpty()) {
            loadingOlderMessages = false
            return
        }
        val mapped =
            history.filter { it.content.isNotBlank() }.map {
                ChatMessage(
                    role = it.role,
                    content = it.content,
                    isToolEvent = isToolHistoryEntry(it),
                )
            }
        if (mapped.isEmpty()) {
            loadingOlderMessages = false
            return
        }
        suppressAutoScroll = true
        messagesListView.items.addAll(0, mapped)
        suppressAutoScroll = false
        Platform.runLater {
            messagesListView.scrollTo(mapped.size.coerceAtLeast(1))
            loadingOlderMessages = false
            updateMeta()
        }
    }

    fun addAssistantMessage(text: String) {
        val items = messagesListView.items
        val normalizedText =
            if (text.isBlank()) {
                "(No text response. See tool results above.)"
            } else {
                text
            }
        val loadingIndex = findLatestLoadingPlaceholderIndex(items)
        if (loadingIndex >= 0) {
            items[loadingIndex] = ChatMessage("assistant", normalizedText)
            waitingForAssistant.set(false)
            updateRuntimeStatus()
            updateMeta()
            scrollToBottom()
            return
        }

        items.add(ChatMessage("assistant", normalizedText))
        waitingForAssistant.set(false)
        updateRuntimeStatus()
        updateMeta()
        scrollToBottom()
    }

    /**
     * Appends a concise tool-call event line to the conversation.
     *
     * @param event Tool invocation event
     */
    fun addToolCallEvent(event: ToolCallEvent) {
        if (event.phase != ToolCallPhase.FINISHED) return
        val status = if (event.result.success) "ok" else "error"
        val baseSummary = "Tool ${event.toolId} (${event.durationMillis}ms) \u00B7 $status"
        val details = event.result.content.trim()
        val summary =
            if (details.isNotBlank()) {
                val excerpt =
                    details
                        .lineSequence()
                        .firstOrNull()
                        .orEmpty()
                        .take(120)
                "$baseSummary \u00B7 $excerpt"
            } else if (!event.result.error.isNullOrBlank()) {
                "$baseSummary \u00B7 ${event.result.error}"
            } else {
                baseSummary
            }
        val items = messagesListView.items
        val loadingIndex = findLatestLoadingPlaceholderIndex(items)
        val eventMessage = ChatMessage("assistant", summary, isToolEvent = true)
        if (loadingIndex >= 0) {
            items.add(loadingIndex, eventMessage)
        } else {
            items.add(eventMessage)
        }
        scrollToBottom()
        updateMeta()
    }

    /**
     * Updates the dedicated tool-activity indicator in the conversation header.
     *
     * @param activeCount Number of active tool calls
     * @param latestToolId Last tool identifier that started execution
     */
    fun updateToolActivity(
        activeCount: Int,
        latestToolId: String?,
    ) {
        activeToolCalls = activeCount.coerceAtLeast(0)
        this.latestToolId = latestToolId
        updateRuntimeStatus()
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
                items[assistantIndex] = ChatMessage("assistant", loadingToken)
                waitingForAssistant.set(true)
                updateRuntimeStatus()
                updateMeta()
                scrollToBottom()
                onSendMessage?.invoke(m.content)
                return
            }
        }
    }

    fun clearMessages() {
        messagesListView.items.clear()
        waitingForAssistant.set(false)
        updateRuntimeStatus()
        updateMeta()
    }

    private fun scrollToBottom() {
        val count = messagesListView.items.size
        if (count > 0) {
            val target = count - 1
            messagesListView.scrollTo(target)
            // Dynamic markdown cell heights can settle over multiple pulses.
            Platform.runLater {
                messagesListView.scrollTo(target)
                Platform.runLater {
                    messagesListView.scrollTo(target)
                }
            }
        }
    }

    private fun installTopScrollListener() {
        val verticalScrollBar =
            messagesListView
                .lookupAll(".scroll-bar")
                .filterIsInstance<ScrollBar>()
                .firstOrNull { it.orientation == Orientation.VERTICAL } ?: return
        verticalScrollBar.valueProperty().addListener { _, _, newValue ->
            if (loadingOlderMessages || suppressAutoScroll) return@addListener
            if (newValue.toDouble() <= 0.02) {
                loadingOlderMessages = true
                onLoadOlderMessages?.invoke() ?: run { loadingOlderMessages = false }
            }
        }
    }

    private fun updateMeta(status: String? = null) {
        // Header meta label was removed by UX design; status is shown in runtime row below messages.
    }

    fun updateResponseMetrics(durationMillis: Long) {
        val seconds = durationMillis / 1000.0
        val rounded = String.format("%.2fs", seconds)
        Platform.runLater { updateMeta("Last response in $rounded") }
    }

    /**
     * Updates the todo summary chip shown in the conversation header.
     *
     * @param total Total todos
     * @param pending Pending todos
     */
    fun updateTodoSummary(
        total: Int,
        open: Int,
        inProgress: Int,
        completed: Int,
        cancelled: Int,
    ) {
        todoInfoLabel.text = "Open $open of $total"
        todoSummaryTooltip.text = "Open: $open\nIn Progress: $inProgress\nDone: $completed\nCancelled: $cancelled\nTotal: $total"
    }

    private fun updateRuntimeStatus() {
        val active = waitingForAssistant.get() || activeToolCalls > 0
        assistantBusyContainer.isManaged = active
        assistantBusyContainer.isVisible = active
        assistantBusyLabel.text =
            when {
                activeToolCalls > 0 -> {
                    val suffix = if (latestToolId.isNullOrBlank()) "" else " · $latestToolId"
                    "Tools running ($activeToolCalls)$suffix"
                }
                waitingForAssistant.get() -> "Waiting for model response"
                else -> "Idle"
            }
    }

    /**
     * Finds the latest assistant loading placeholder in the message list.
     *
     * @param items Current message list
     * @return Index of the last loading placeholder or -1 when none exists
     */
    private fun findLatestLoadingPlaceholderIndex(items: List<ChatMessage>): Int {
        for (i in items.lastIndex downTo 0) {
            val msg = items[i]
            if (msg.role == "assistant" && msg.content == loadingToken) {
                return i
            }
        }
        return -1
    }

    private fun isToolHistoryEntry(message: Message): Boolean {
        val metadata = message.metadata ?: return false
        return metadata.contains("\"type\":\"tool_call\"")
    }

    private inner class ChatMessageCell : ListCell<ChatMessage>() {
        private val timeFormatter =
            DateTimeFormatter
                .ofPattern("HH:mm")
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

        override fun updateItem(
            item: ChatMessage?,
            empty: Boolean,
        ) {
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
                if (item.content != loadingToken) {
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
                if (item.isToolEvent) {
                    styleClass.add("chat-row-tool")
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

        private fun createAvatarSlot(
            item: ChatMessage,
            isGrouped: Boolean,
        ): Region {
            if (isGrouped) {
                val spacer = Region()
                spacer.styleClass.add("chat-avatar-spacer")
                return spacer
            }

            val avatar = Label(if (item.role == "user") "You" else "AI")
            if (item.isToolEvent) {
                avatar.text = "Tool"
            }
            avatar.styleClass.addAll("chat-avatar", if (item.role == "user") "chat-avatar-user" else "chat-avatar-assistant")
            return avatar
        }

        private fun createContentArea(
            item: ChatMessage,
            isGrouped: Boolean,
        ): VBox {
            if (isGrouped) {
                val contentBody = createMessageBody(item)

                val actionButtons = createActionButtons(copyButton, copyRawButton)
                copyButton.setOnAction { copyToClipboard(item.content) }
                copyRawButton.setOnAction { copyToClipboard(item.content) }

                val contentArea = VBox(contentBody, actionButtons)
                contentArea.styleClass.add("chat-content-area")
                contentArea.isFillWidth = true
                return contentArea
            }

            val roleName =
                Label(
                    when {
                        item.isToolEvent -> "Tool"
                        item.role == "user" -> "You"
                        else -> "Assistant"
                    },
                )
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

            val actionButtons = createActionButtons(copyButton, copyRawButton, retryButton)

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

        /**
         * Creates the per-message action container and shows it only while hovering the message row.
         *
         * @param buttons Action buttons shown for the message
         * @return Hover-aware actions container
         */
        private fun createActionButtons(vararg buttons: Button): HBox {
            val actionButtons = HBox(*buttons)
            actionButtons.styleClass.add("chat-action-buttons")
            actionButtons.visibleProperty().bind(hoverProperty())
            return actionButtons
        }

        private fun createMessageBody(item: ChatMessage): Region {
            if (item.content == loadingToken) {
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
