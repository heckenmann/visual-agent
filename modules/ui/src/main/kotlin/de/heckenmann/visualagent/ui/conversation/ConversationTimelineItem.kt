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
        val id: String,
    ) : ConversationTimelineItem {
        override val stableKey = id
    }

    data class PendingUser(
        val content: String,
        val id: String,
    ) : ConversationTimelineItem {
        override val stableKey = id
    }

    data class MessageEntry(
        val message: Message,
        val chronologicalIndex: Int,
        val isStreaming: Boolean = false,
    ) : ConversationTimelineItem {
        override val stableKey = message.id ?: "temporary-message-$chronologicalIndex"
    }

    data class Persisted(
        val message: Message,
        val chronologicalIndex: Int,
    ) : ConversationTimelineItem {
        override val stableKey = message.id ?: "temporary-message-$chronologicalIndex"
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
    streamingEntryId: String? = null,
    pendingUserEntryId: String? = null,
    streamingMessages: List<Message> = emptyList(),
): List<ConversationTimelineItem> =
    buildList {
        if (includeInlineComposer) add(ConversationTimelineItem.InlineComposer)
        if (showWaitingIndicator) add(ConversationTimelineItem.Waiting)
        val uniqueHistory = history.distinctPersistedMessages()
        val persistedIds = uniqueHistory.mapNotNull { message -> message.id }.toSet()
        val activeStreamingMessages =
            streamingMessages.ifEmpty {
                if (streamingContent.isNotEmpty() && streamingEntryId != null) {
                    listOf(Message("assistant", streamingContent, id = streamingEntryId))
                } else {
                    emptyList()
                }
            }
        activeStreamingMessages.forEachIndexed { index, message ->
            if (message.id != null && message.id !in persistedIds) {
                add(ConversationTimelineItem.MessageEntry(message, -2 - index, true))
            }
        }
        if (pendingUserMessage != null && pendingUserEntryId != null && pendingUserEntryId !in persistedIds) {
            add(ConversationTimelineItem.MessageEntry(Message("user", pendingUserMessage, id = pendingUserEntryId), -1))
        }
        val toolMessagesByParent =
            uniqueHistory
                .filter {
                    it.role == "tool" && it.parentAssistantTurnId != null
                }.groupBy { it.parentAssistantTurnId }
        val structuredTurnIds = uniqueHistory.filter { it.role == "assistant" && it.assistantToolTurn }.mapNotNull { it.id }.toSet()
        val attachedToolIds =
            toolMessagesByParent
                .filterKeys { it in structuredTurnIds }
                .values
                .flatten()
                .mapNotNull { it.id }
                .toSet()
        val persisted: List<ConversationTimelineItem> =
            uniqueHistory.indices.reversed().mapNotNull { index ->
                val message = uniqueHistory[index]
                val parentId = message.id
                when {
                    message.id != null && message.id in attachedToolIds -> null
                    message.role == "assistant" && message.assistantToolTurn && parentId != null -> {
                        val parent = ConversationTimelineItem.Persisted(message, index)
                        val tools =
                            toolMessagesByParent[parentId]
                                .orEmpty()
                                .sortedWith(compareBy({ it.turnOrder ?: Int.MAX_VALUE }, { it.timelineSequence ?: Long.MAX_VALUE }))
                                .map { tool ->
                                    ConversationTimelineItem.Persisted(
                                        tool,
                                        uniqueHistory.indexOfFirst { it.id == tool.id }.coerceAtLeast(0),
                                    )
                                }
                        ConversationTimelineItem.PersistedGroup(ConversationMessageGroup(listOf(parent) + tools))
                    }
                    else -> ConversationTimelineItem.Persisted(message, index)
                }
            }
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
                val message =
                    when (item) {
                        is ConversationTimelineItem.Persisted -> item.message
                        is ConversationTimelineItem.PersistedGroup ->
                            item.group.messages
                                .first()
                                .message
                        else -> error("Unexpected timeline item in persisted history")
                    }
                TimelineEntry<ConversationTimelineItem>(
                    sequence = message.timelineSequence ?: 0,
                    timestamp = message.createdAtEpochMillis ?: Long.MIN_VALUE + (persisted.size - index),
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
        var userRun = mutableListOf<ConversationTimelineItem.Persisted>()

        /** Emits the current adjacent user-message run as one presentation group. */
        fun flushUserRun() {
            when (userRun.size) {
                0 -> Unit
                1 -> add(ConversationTimelineItem.MessageEntry(userRun.single().message, userRun.single().chronologicalIndex))
                else -> add(ConversationTimelineItem.PersistedGroup(ConversationMessageGroup(userRun.toList())))
            }
            userRun = mutableListOf()
        }
        merged.forEach { item ->
            if (item is ConversationTimelineItem.Persisted && item.message.role == "user") {
                userRun += item
            } else {
                flushUserRun()
                when (item) {
                    is ConversationTimelineItem.Persisted ->
                        when {
                            item.message.role == "assistant" && !item.message.assistantToolTurn ->
                                add(ConversationTimelineItem.MessageEntry(item.message, item.chronologicalIndex))
                            item.message.role == "assistant" ->
                                add(ConversationTimelineItem.PersistedGroup(ConversationMessageGroup(listOf(item))))
                            else -> add(item)
                        }
                    else -> add(item)
                }
            }
        }
        flushUserRun()
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
