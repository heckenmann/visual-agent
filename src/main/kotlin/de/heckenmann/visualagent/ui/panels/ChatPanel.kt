package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.ui.FxmlLoader
import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextArea
import javafx.scene.control.Tooltip
import javafx.scene.image.ImageView
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
@Lazy
class ChatPanel : Region() {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    @FXML private lateinit var rootBorderPane: BorderPane

    @FXML private lateinit var messagesScrollPane: ScrollPane

    @FXML private lateinit var messagesContainer: VBox

    @FXML private lateinit var inputTextField: TextArea

    @FXML private lateinit var sendButton: Button

    @FXML private lateinit var clearChatButton: Button

    @FXML private lateinit var todoInfoLabel: Label

    @FXML private lateinit var openTodosButton: Button

    @FXML private lateinit var assistantBusyContainer: HBox

    @FXML private lateinit var assistantBusyLabel: Label

    @FXML private lateinit var conversationIconImage: ImageView

    private var onSendMessage: ((String) -> Unit)? = null
    private var onClearConversation: (() -> Unit)? = null
    private var onOpenTodos: (() -> Unit)? = null
    private var onLoadOlderMessages: (() -> Unit)? = null
    private val todoSummaryTooltip = Tooltip()
    private val loadingToken = "__loading__"
    private val waitingForAssistant = SimpleBooleanProperty(false)
    private val toolHistoryParser = ChatToolHistoryParser()
    private val messageMapper = ChatMessageMapper(toolHistoryParser)
    private lateinit var messageList: ChatMessageListController
    private lateinit var runtimeStatus: ChatRuntimeStatusController

    init {
        styleClass.add("chat-panel")
        val root = FxmlLoader.load(this, "chat-panel.fxml")
        children.add(root)
    }

    @FXML
    private fun initialize() {
        runtimeStatus =
            ChatRuntimeStatusController(
                assistantBusyContainer = assistantBusyContainer,
                assistantBusyLabel = assistantBusyLabel,
                todoInfoLabel = todoInfoLabel,
                todoSummaryTooltip = todoSummaryTooltip,
            )
        messageList =
            ChatMessageListController(
                messagesScrollPane = messagesScrollPane,
                messagesContainer = messagesContainer,
                renderer =
                    ChatMessageRenderer(
                        loadingToken = loadingToken,
                        timeFormatter = timeFormatter,
                        previousRole = { index -> messageList.messages.getOrNull(index - 1)?.role },
                        retryAtRow = { row -> retryAssistantAt(messageList.messageRows.indexOf(row)) },
                    ),
                loadingToken = loadingToken,
            )
        ChatPanelInitializer(
            owner = this,
            rootBorderPane = rootBorderPane,
            messagesScrollPane = messagesScrollPane,
            inputTextField = inputTextField,
            sendButton = sendButton,
            clearChatButton = clearChatButton,
            todoInfoLabel = todoInfoLabel,
            openTodosButton = openTodosButton,
            assistantBusyContainer = assistantBusyContainer,
            conversationIconImage = conversationIconImage,
            todoSummaryTooltip = todoSummaryTooltip,
            messageList = messageList,
        ).initialize(
            sendMessage = ::sendMessage,
            clearConversation = {
                clearMessages()
                onClearConversation?.invoke()
            },
            openTodos = { onOpenTodos?.invoke() },
            loadOlderMessages = { onLoadOlderMessages?.invoke() ?: run { messageList.loadingOlderMessages = false } },
        )
        sendButton.disableProperty().bind(inputTextField.textProperty().isEmpty.or(waitingForAssistant))
        updateMeta()
        updateTodoSummary(total = 0, open = 0, inProgress = 0, completed = 0, cancelled = 0)
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
        messageList.append(ChatMessage("user", text))
        messageList.append(ChatMessage("assistant", loadingToken))
        waitingForAssistant.set(true)
        updateRuntimeStatus()
        updateMeta()
        onSendMessage?.invoke(text)
    }

    fun setOnSendMessage(callback: (String) -> Unit) {
        onSendMessage = callback
    }

    fun setOnClearConversation(callback: () -> Unit) {
        onClearConversation = callback
    }

    /** Starts an assistant loading state without a preceding user message. */
    fun startAssistantLoading() {
        messageList.append(ChatMessage("assistant", loadingToken))
        waitingForAssistant.set(true)
        updateRuntimeStatus()
        updateMeta()
    }

    /** Registers a callback that opens the dedicated todos panel. */
    fun setOnOpenTodos(callback: () -> Unit) {
        onOpenTodos = callback
    }

    /** Registers a callback for lazy history pagination. */
    fun setOnLoadOlderMessages(callback: () -> Unit) {
        onLoadOlderMessages = callback
    }

    fun setConversationHistory(history: List<Message>) {
        messageList.setMessages(history.mapNotNull(messageMapper::fromHistory))
        waitingForAssistant.set(false)
        updateRuntimeStatus()
        updateMeta()
    }

    /** Prepends older messages without forcing scroll-to-bottom. */
    fun prependConversationHistory(history: List<Message>) {
        val mapped = history.mapNotNull(messageMapper::fromHistory)
        if (mapped.isEmpty()) {
            messageList.loadingOlderMessages = false
            return
        }
        messageList.prepend(mapped)
        updateMeta()
    }

    fun addAssistantMessage(text: String) {
        val normalizedText =
            if (text.isBlank()) {
                "(No text response. See tool results above.)"
            } else {
                text
            }
        val loadingIndex = messageList.latestLoadingIndex()
        if (loadingIndex >= 0) {
            messageList.replace(loadingIndex, messageList.messages[loadingIndex].copy(content = normalizedText))
            waitingForAssistant.set(false)
            updateRuntimeStatus()
            updateMeta()
            messageList.scrollToBottom()
            return
        }

        messageList.append(ChatMessage("assistant", normalizedText))
        waitingForAssistant.set(false)
        updateRuntimeStatus()
        updateMeta()
    }

    /** Updates the active assistant placeholder with streamed content. */
    fun updateStreamingAssistantMessage(content: String) {
        messageList.updateStreaming(content)
    }

    /** Finalizes streaming state once all chunks have been received. */
    fun finishStreamingAssistantMessage(finalText: String) {
        val normalized =
            if (finalText.isBlank()) {
                "(No text response. See tool results above.)"
            } else {
                finalText
            }
        messageList.updateStreaming(normalized)
        messageList.scrollToBottom(force = true)
        waitingForAssistant.set(false)
        updateRuntimeStatus()
        updateMeta()
    }

    /** Appends a concise tool-call event line to the conversation. */
    fun addToolCallEvent(event: ToolCallEvent) {
        if (event.phase != ToolCallPhase.FINISHED) return
        val eventMessage = messageMapper.fromToolEvent(event)
        val loadingIndex = messageList.latestLoadingIndex()
        if (loadingIndex >= 0) {
            messageList.insert(loadingIndex, eventMessage)
        } else {
            messageList.append(eventMessage)
        }
        updateMeta()
    }

    /** Updates the dedicated tool-activity indicator. */
    fun updateToolActivity(
        activeCount: Int,
        latestToolId: String?,
    ) {
        runtimeStatus.updateToolActivity(activeCount, latestToolId)
    }

    /**
     * Retry sending the user message that precedes the assistant message at [assistantIndex].
     */
    fun retryAssistantAt(assistantIndex: Int) {
        if (assistantIndex < 0 || assistantIndex >= messageList.messages.size) return
        for (i in assistantIndex - 1 downTo 0) {
            val m = messageList.messages[i]
            if (m.role == "user") {
                messageList.replace(assistantIndex, messageList.messages[assistantIndex].copy(content = loadingToken))
                waitingForAssistant.set(true)
                updateRuntimeStatus()
                updateMeta()
                messageList.scrollToBottom(force = true)
                onSendMessage?.invoke(m.content)
                return
            }
        }
    }

    fun clearMessages() {
        messageList.clear()
        waitingForAssistant.set(false)
        updateRuntimeStatus()
        updateMeta()
    }

    /**
     * Focuses the message input and scrolls the conversation to the newest message.
     */
    fun focusInputAndScrollToBottom() {
        Platform.runLater {
            messageList.scrollToBottom(force = true)
            inputTextField.requestFocus()
            inputTextField.positionCaret(inputTextField.text.length)
        }
    }

    private fun updateMeta(status: String? = null) {}

    fun updateResponseMetrics(durationMillis: Long) {
        val seconds = durationMillis / 1000.0
        val rounded = String.format("%.2fs", seconds)
        Platform.runLater { updateMeta("Last response in $rounded") }
    }

    /** Updates the todo summary chip shown in the conversation header. */
    fun updateTodoSummary(
        total: Int,
        open: Int,
        inProgress: Int,
        completed: Int,
        cancelled: Int,
    ) {
        runtimeStatus.updateTodoSummary(total, open, inProgress, completed, cancelled)
    }

    private fun updateRuntimeStatus() = runtimeStatus.updateRuntimeStatus()
}
