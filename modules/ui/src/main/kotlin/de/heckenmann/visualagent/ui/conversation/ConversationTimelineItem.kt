@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.ui.todo.TodoResponseState
import de.heckenmann.visualagent.protocol.ConversationMessage as Message

internal sealed interface ConversationTimelineItem {
    val stableKey: String

    data object InlineComposer : ConversationTimelineItem {
        override val stableKey = "conversation-input"
    }

    data object Waiting : ConversationTimelineItem {
        override val stableKey = "streaming-indicator"
    }

    data class Streaming(
        val content: String,
    ) : ConversationTimelineItem {
        override val stableKey = "streaming-assistant"
    }

    data class PendingUser(
        val content: String,
    ) : ConversationTimelineItem {
        override val stableKey = "pending-user"
    }

    data class Persisted(
        val message: Message,
        val chronologicalIndex: Int,
    ) : ConversationTimelineItem {
        override val stableKey = message.id?.let { "message:$it" } ?: "temporary-message-$chronologicalIndex"
    }

    data class PersistedGroup(
        val group: ConversationMessageGroup,
    ) : ConversationTimelineItem {
        override val stableKey = group.stableKey
    }

    data class TodoCard(
        val todo: TodoItem,
        val responseState: TodoResponseState,
        val deleted: Boolean,
    ) : ConversationTimelineItem {
        override val stableKey = "todo:${todo.id}"
    }

    data object OlderHistoryLoading : ConversationTimelineItem {
        override val stableKey = "loading-older"
    }

    data object Empty : ConversationTimelineItem {
        override val stableKey = "conversation-empty"
    }
}

internal fun buildConversationTimeline(
    history: List<Message>,
    pendingUserMessage: String?,
    streamingContent: String,
    showWaitingIndicator: Boolean,
    showOlderHistoryLoading: Boolean,
    includeInlineComposer: Boolean,
    todos: List<TodoItem> = emptyList(),
    deletedTodoSnapshots: Map<String, TodoItem> = emptyMap(),
    todoResponses: Map<String, TodoResponseState> = emptyMap(),
): List<ConversationTimelineItem> =
    buildList {
        if (includeInlineComposer) add(ConversationTimelineItem.InlineComposer)
        if (showWaitingIndicator) add(ConversationTimelineItem.Waiting)
        if (streamingContent.isNotEmpty()) add(ConversationTimelineItem.Streaming(streamingContent))
        if (pendingUserMessage != null) add(ConversationTimelineItem.PendingUser(pendingUserMessage))
        val persisted = history.indices.reversed().map { index -> ConversationTimelineItem.Persisted(history[index], index) }
        val cards =
            (todos.map { todo -> todo to false } + deletedTodoSnapshots.values.map { todo -> todo to true })
                .distinctBy { (todo, _) -> todo.id }
                .map { (todo, deleted) ->
                    ConversationTimelineItem.TodoCard(
                        todo = todo,
                        responseState = todoResponses[todo.id] ?: TodoResponseState(),
                        deleted = deleted,
                    )
                }
        val messageEntries: List<TimelineEntry<ConversationTimelineItem>> =
            persisted.mapIndexed { index, item ->
                TimelineEntry<ConversationTimelineItem>(
                    sequence = item.message.timelineSequence ?: 0,
                    timestamp = item.message.createdAtEpochMillis ?: Long.MIN_VALUE + (persisted.size - index),
                    typeRank = 0,
                    fallbackOrder = index,
                    item = item,
                )
            }
        val todoEntries: List<TimelineEntry<ConversationTimelineItem>> =
            cards.mapIndexed { index, item ->
                TimelineEntry<ConversationTimelineItem>(
                    sequence = item.todo.timelineSequence,
                    timestamp =
                        item.todo.updatedAt?.toEpochMilli()
                            ?: item.todo.createdAt?.toEpochMilli()
                            ?: Long.MIN_VALUE / 2 + index,
                    typeRank = 1,
                    fallbackOrder = index,
                    item = item,
                )
            }
        val entries = messageEntries + todoEntries
        val merged =
            entries
                .sortedWith(
                    compareByDescending<TimelineEntry<ConversationTimelineItem>> { it.sequence }
                        .thenByDescending { it.timestamp }
                        .thenByDescending { it.typeRank }
                        .thenBy { it.fallbackOrder },
                ).map { it.item }
        var messageRun = mutableListOf<ConversationTimelineItem.Persisted>()

        /** Flushes the current consecutive message run into grouped timeline items. */
        fun flushMessageRun() {
            if (messageRun.isNotEmpty()) {
                groupConsecutiveConversationMessages(messageRun).forEach { group -> add(ConversationTimelineItem.PersistedGroup(group)) }
                messageRun = mutableListOf()
            }
        }
        merged.forEach { item ->
            if (item is ConversationTimelineItem.Persisted) {
                messageRun += item
            } else {
                flushMessageRun()
                add(item)
            }
        }
        flushMessageRun()
        if (showOlderHistoryLoading) add(ConversationTimelineItem.OlderHistoryLoading)
        if (history.isEmpty() &&
            todos.isEmpty() &&
            deletedTodoSnapshots.isEmpty() &&
            pendingUserMessage == null &&
            streamingContent.isEmpty() &&
            !showWaitingIndicator
        ) {
            add(ConversationTimelineItem.Empty)
        }
    }

private data class TimelineEntry<T>(
    val sequence: Long,
    val timestamp: Long,
    val typeRank: Int,
    val fallbackOrder: Int,
    val item: T,
)
