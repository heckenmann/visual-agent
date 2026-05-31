package de.heckenmann.visualagent.ui.panels

import javafx.application.Platform
import javafx.scene.control.ScrollPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import kotlin.math.abs

/**
 * Owns conversation message state, row creation, grouping, scrolling, and streaming updates.
 *
 * @property messagesScrollPane Scroll container
 * @property messagesContainer VBox containing rendered rows
 * @property renderer Row renderer
 * @property loadingToken Placeholder text for pending assistant responses
 */
internal class ChatMessageListController(
    private val messagesScrollPane: ScrollPane,
    private val messagesContainer: VBox,
    private val renderer: ChatMessageRenderer,
    private val loadingToken: String,
) {
    val messages = mutableListOf<ChatMessage>()
    val messageRows = mutableListOf<HBox>()
    var loadingOlderMessages = false

    private var suppressAutoScroll = false
    private var latestStreamingContent = ""
    private var pendingStreamingUiUpdate = false
    private var lastStreamingUiNanos = 0L
    private var lastStreamingScrollNanos = 0L

    /**
     * Replaces the full conversation history.
     */
    fun setMessages(history: List<ChatMessage>) {
        suppressAutoScroll = true
        clear()
        history.forEach { appendMessageInternal(it, autoScroll = false) }
        suppressAutoScroll = false
        scrollToBottom(force = true)
    }

    /**
     * Prepends older messages while preserving approximate scroll position.
     */
    fun prepend(history: List<ChatMessage>) {
        if (history.isEmpty()) {
            loadingOlderMessages = false
            return
        }
        val previousHeight = messagesContainer.height
        val previousVvalue = messagesScrollPane.vvalue
        suppressAutoScroll = true
        history.forEachIndexed { index, message ->
            messages.add(index, message)
            val row = renderer.createMessageRow(message, index)
            messageRows.add(index, row)
            messagesContainer.children.add(index, row)
        }
        refreshGroupingStyles()
        suppressAutoScroll = false
        Platform.runLater {
            val newHeight = messagesContainer.height
            val delta = (newHeight - previousHeight).coerceAtLeast(0.0)
            messagesScrollPane.vvalue =
                if (newHeight <= 0.0) {
                    previousVvalue
                } else {
                    ((previousVvalue * previousHeight + delta) / newHeight).coerceIn(0.0, 1.0)
                }
            loadingOlderMessages = false
        }
    }

    /**
     * Appends a message to the bottom.
     */
    fun append(message: ChatMessage) {
        appendMessageInternal(message, autoScroll = true)
    }

    /**
     * Inserts a message at a specific index.
     */
    fun insert(
        index: Int,
        message: ChatMessage,
    ) {
        messages.add(index, message)
        val row = renderer.createMessageRow(message, index)
        messageRows.add(index, row)
        messagesContainer.children.add(index, row)
        refreshGroupingStyles()
        scrollToBottom()
    }

    /**
     * Replaces one message row.
     */
    fun replace(
        index: Int,
        message: ChatMessage,
    ) {
        if (index !in messages.indices) return
        messages[index] = message
        val replacement = renderer.createMessageRow(message, index)
        messageRows[index] = replacement
        messagesContainer.children[index] = replacement
        refreshGroupingStylesAround(index)
    }

    /**
     * Clears all rendered messages.
     */
    fun clear() {
        messages.clear()
        messageRows.clear()
        messagesContainer.children.clear()
        latestStreamingContent = ""
    }

    /**
     * Updates the active assistant row with streamed content.
     */
    fun updateStreaming(content: String) {
        latestStreamingContent = content.ifBlank { " " }
        val now = System.nanoTime()
        if (pendingStreamingUiUpdate && abs(now - lastStreamingUiNanos) < 50_000_000L) return
        pendingStreamingUiUpdate = true
        Platform.runLater {
            pendingStreamingUiUpdate = false
            lastStreamingUiNanos = System.nanoTime()
            applyStreamingAssistantUpdate(latestStreamingContent)
        }
    }

    /**
     * Returns the most recent loading placeholder index.
     */
    fun latestLoadingIndex(): Int {
        for (i in messages.lastIndex downTo 0) {
            val msg = messages[i]
            if (msg.role == "assistant" && msg.content == loadingToken) return i
        }
        return -1
    }

    /**
     * Scrolls to bottom when appropriate.
     */
    fun scrollToBottom(force: Boolean = false) {
        if (messages.isEmpty()) return
        if (!force && !isNearBottom()) return
        Platform.runLater { messagesScrollPane.vvalue = 1.0 }
    }

    private fun appendMessageInternal(
        message: ChatMessage,
        autoScroll: Boolean,
    ) {
        val index = messages.size
        messages.add(message)
        val row = renderer.createMessageRow(message, index)
        messageRows.add(row)
        messagesContainer.children.add(row)
        refreshGroupingStylesAround(index)
        if (autoScroll && !suppressAutoScroll) scrollToBottom()
    }

    private fun applyStreamingAssistantUpdate(rendered: String) {
        val loadingIndex = latestLoadingIndex()
        if (loadingIndex >= 0) {
            val existing = messages[loadingIndex]
            if (existing.content != rendered) replace(loadingIndex, existing.copy(content = rendered))
        } else if (messages.isNotEmpty() && messages.last().role == "assistant") {
            val lastIndex = messages.lastIndex
            val existing = messages[lastIndex]
            if (existing.content != rendered) replace(lastIndex, existing.copy(content = rendered))
        } else {
            append(ChatMessage("assistant", rendered))
        }
        val now = System.nanoTime()
        if (abs(now - lastStreamingScrollNanos) > 120_000_000L) {
            lastStreamingScrollNanos = now
            scrollToBottom()
        }
    }

    private fun isNearBottom(): Boolean = messagesScrollPane.vvalue >= 0.94

    private fun refreshGroupingStyles() {
        messages.indices.forEach { idx -> applyGroupingStyle(idx) }
    }

    private fun refreshGroupingStylesAround(index: Int) {
        val range = (index - 1).coerceAtLeast(0)..(index + 1).coerceAtMost(messages.lastIndex)
        for (idx in range) applyGroupingStyle(idx)
    }

    private fun applyGroupingStyle(index: Int) {
        if (index !in messages.indices || index !in messageRows.indices) return
        val row = messageRows[index]
        val grouped = index > 0 && messages[index - 1].role == messages[index].role
        if (grouped) {
            if (!row.styleClass.contains("chat-row-grouped")) row.styleClass.add("chat-row-grouped")
        } else {
            row.styleClass.remove("chat-row-grouped")
        }
    }
}
