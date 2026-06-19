package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.ui.FxmlLoader
import de.heckenmann.visualagent.ui.panels.chat.ChatConversationEventsController
import de.heckenmann.visualagent.ui.panels.chat.ChatMessage
import de.heckenmann.visualagent.ui.panels.chat.ChatMessageListController
import de.heckenmann.visualagent.ui.panels.chat.ChatMessageMapper
import de.heckenmann.visualagent.ui.panels.chat.ChatMessageRenderer
import de.heckenmann.visualagent.ui.panels.chat.ChatPanelInitializer
import de.heckenmann.visualagent.ui.panels.chat.ChatRuntimeStatusController
import de.heckenmann.visualagent.ui.panels.chat.ChatToolHistoryParser
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

/** Conversation workspace panel with markdown, streaming, tool previews, todos, and input handling. */
@Component
@Lazy
class ChatPanel : Region() {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    @FXML private lateinit var rootBorderPane: BorderPane

    @FXML private lateinit var messagesScrollPane: ScrollPane

    @FXML private lateinit var messagesContainer: VBox

    @FXML private lateinit var emptyConversationState: VBox

    @FXML private lateinit var inputTextField: TextArea

    @FXML private lateinit var sendButton: Button

    @FXML private lateinit var clearChatButton: Button

    @FXML private lateinit var todoInfoLabel: Label

    @FXML private lateinit var responseMetaLabel: Label

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
    private lateinit var conversationEvents: ChatConversationEventsController

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
                responseMetaLabel = responseMetaLabel,
                todoSummaryTooltip = todoSummaryTooltip,
            )
        messageList =
            ChatMessageListController(
                messagesScrollPane = messagesScrollPane,
                messagesContainer = messagesContainer,
                emptyConversationState = emptyConversationState,
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
        conversationEvents =
            ChatConversationEventsController(
                messageList = messageList,
                loadingToken = loadingToken,
                waitingForAssistant = waitingForAssistant,
                updateRuntimeStatus = ::updateRuntimeStatus,
                sendMessage = { text -> onSendMessage?.invoke(text) },
                mapToolEvent = messageMapper::fromToolEvent,
            )
        sendButton.disableProperty().bind(inputTextField.textProperty().isEmpty.or(waitingForAssistant))
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
        onSendMessage?.invoke(text)
    }

    /**
     * Registers the callback invoked when the user submits a message.
     */
    fun setOnSendMessage(callback: (String) -> Unit) {
        onSendMessage = callback
    }

    /**
     * Registers the callback invoked after the user clears the conversation UI.
     */
    fun setOnClearConversation(callback: () -> Unit) {
        onClearConversation = callback
    }

    /** Starts an assistant loading state without a preceding user message. */
    fun startAssistantLoading() {
        messageList.append(ChatMessage("assistant", loadingToken))
        waitingForAssistant.set(true)
        updateRuntimeStatus()
    }

    /** Registers a callback that opens the dedicated todos panel. */
    fun setOnOpenTodos(callback: () -> Unit) {
        onOpenTodos = callback
    }

    /** Registers a callback for lazy history pagination. */
    fun setOnLoadOlderMessages(callback: () -> Unit) {
        onLoadOlderMessages = callback
    }

    /**
     * Replaces the visible conversation with persisted history.
     */
    fun setConversationHistory(history: List<Message>) {
        messageList.setMessages(history.mapNotNull(messageMapper::fromHistory))
        waitingForAssistant.set(false)
        updateRuntimeStatus()
    }

    /** Prepends older messages without forcing scroll-to-bottom. */
    fun prependConversationHistory(history: List<Message>) {
        val mapped = history.mapNotNull(messageMapper::fromHistory)
        if (mapped.isEmpty()) {
            messageList.loadingOlderMessages = false
            return
        }
        messageList.prepend(mapped)
    }

    /**
     * Appends a completed assistant message and clears waiting indicators.
     */
    fun addAssistantMessage(text: String) {
        conversationEvents.addAssistantMessage(text)
    }

    /** Updates the active assistant placeholder with streamed content. */
    fun updateStreamingAssistantMessage(content: String) {
        conversationEvents.updateStreamingAssistantMessage(content)
    }

    /** Finalizes streaming state once all chunks have been received. */
    fun finishStreamingAssistantMessage(finalText: String) {
        conversationEvents.finishStreamingAssistantMessage(finalText)
    }

    /** Appends a concise tool-call event line to the conversation. */
    fun addToolCallEvent(event: ToolCallEvent) {
        conversationEvents.addToolCallEvent(event)
    }

    /** Appends one synthetic thinking event block. */
    fun addThinkingEvent(thinkingContent: String) {
        conversationEvents.addThinkingEvent(thinkingContent)
    }

    /** Updates the dedicated tool-activity indicator. */
    fun updateToolActivity(
        activeCount: Int,
        latestToolId: String?,
    ) {
        runtimeStatus.updateToolActivity(activeCount, latestToolId)
    }

    /** Retry sending the user message that precedes the assistant message at [assistantIndex]. */
    fun retryAssistantAt(assistantIndex: Int) {
        conversationEvents.retryAssistantAt(assistantIndex)
    }

    /**
     * Clears the visible message list and resets assistant waiting state.
     */
    fun clearMessages() {
        messageList.clear()
        waitingForAssistant.set(false)
        updateRuntimeStatus()
    }

    /** Focuses the message input and scrolls the conversation to the newest message. */
    fun focusInputAndScrollToBottom() {
        Platform.runLater {
            messageList.scrollToBottom(force = true)
            inputTextField.requestFocus()
            inputTextField.positionCaret(inputTextField.text.length)
        }
    }

    /**
     * Records the last response duration for status display.
     */
    fun updateResponseMetrics(durationMillis: Long) {
        Platform.runLater { runtimeStatus.updateResponseMetrics(durationMillis) }
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
