package de.heckenmann.visualagent.ui.conversation

import de.heckenmann.visualagent.protocol.ConversationMessage
import de.heckenmann.visualagent.protocol.TodoItem

/** Immutable content observations, independent of row grouping and history-page indices. */
internal data class ConversationTimelineScrollSnapshot(
    val entries: List<ConversationScrollEntry>,
) {
    /** Detects updates and inserted activity, but not older entries appended to the timeline. */
    fun hasNewContentSince(previous: ConversationTimelineScrollSnapshot): Boolean {
        val known = previous.entries.associateBy { it.key }
        if (entries.any { entry -> known[entry.key]?.let { it != entry } == true }) return true
        val lastSequence = previous.entries.maxOfOrNull { it.sequence } ?: 0
        if (entries.any { it.key !in known && it.sequence > lastSequence }) return true
        val oldestKnownIndex = entries.indexOfLast { it.key in known }
        if (oldestKnownIndex < 0) return entries.isNotEmpty()
        return entries.take(oldestKnownIndex + 1).any { it.key !in known }
    }
}

/** Content-bearing activity identities used by automatic conversation following. */
internal sealed interface ConversationScrollEntry {
    val key: String
    val sequence: Long

    data class Message(
        override val key: String,
        val message: ConversationMessage,
    ) : ConversationScrollEntry {
        override val sequence: Long get() = message.timelineSequence ?: 0
    }

    data class Todo(
        override val key: String,
        val todo: TodoItem,
        val deleted: Boolean,
        val text: String,
        val executionId: String?,
        val agentId: String?,
        val streaming: Boolean,
        val reviewing: Boolean,
    ) : ConversationScrollEntry {
        override val sequence: Long get() = todo.timelineSequence
    }
}

/** Captures every rendered activity without retaining mutable todo-response holders. */
internal fun conversationTimelineScrollSnapshot(timeline: List<ConversationTimelineItem>): ConversationTimelineScrollSnapshot =
    ConversationTimelineScrollSnapshot(
        buildList {
            timeline.forEach { item ->
                when (item) {
                    is ConversationTimelineItem.MessageEntry -> add(ConversationScrollEntry.Message(item.stableKey, item.message))
                    is ConversationTimelineItem.Persisted -> add(ConversationScrollEntry.Message(item.stableKey, item.message))
                    is ConversationTimelineItem.PersistedGroup ->
                        item.group.messages.forEach { message -> add(ConversationScrollEntry.Message(message.stableKey, message.message)) }
                    is ConversationTimelineItem.Streaming ->
                        add(ConversationScrollEntry.Message(item.stableKey, ConversationMessage("assistant", item.content, id = item.id)))
                    is ConversationTimelineItem.PendingUser ->
                        add(ConversationScrollEntry.Message(item.stableKey, ConversationMessage("user", item.content, id = item.id)))
                    is ConversationTimelineItem.TodoCard ->
                        add(
                            ConversationScrollEntry.Todo(
                                key = item.stableKey,
                                todo = item.todo,
                                deleted = item.deleted,
                                text = item.responseState.text,
                                executionId = item.responseState.executionId,
                                agentId = item.responseState.agentId,
                                streaming = item.responseState.isStreaming,
                                reviewing = item.responseState.isReviewing,
                            ),
                        )
                    ConversationTimelineItem.InlineComposer,
                    ConversationTimelineItem.Empty,
                    ConversationTimelineItem.OlderHistoryLoading,
                    -> Unit
                }
            }
        },
    )
