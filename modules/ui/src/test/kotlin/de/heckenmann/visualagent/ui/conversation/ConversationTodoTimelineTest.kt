package de.heckenmann.visualagent.ui.conversation

import de.heckenmann.visualagent.protocol.ConversationMessage
import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.protocol.TodoState
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies chronological todo-card insertion without synthetic conversation messages. */
class ConversationTodoTimelineTest {
    @Test
    fun `todo card moves to latest activity position when updated`() {
        val history =
            listOf(
                ConversationMessage("user", "old", id = "old", createdAtEpochMillis = 1_000),
                ConversationMessage("assistant", "new", id = "new", createdAtEpochMillis = 3_000),
            )
        val todo =
            TodoItem(
                "todo-1",
                "Analyze parser",
                createdAt = Instant.ofEpochMilli(2_000),
                updatedAt = Instant.ofEpochMilli(2_000),
            )

        val pending = buildConversationTimeline(history, null, "", false, false, false, todos = listOf(todo))
        val completed =
            buildConversationTimeline(
                history,
                null,
                "",
                false,
                false,
                false,
                todos = listOf(todo.copy(status = TodoState.COMPLETED, updatedAt = Instant.ofEpochMilli(4_000))),
            )

        assertEquals(listOf("message:new", "todo:todo-1", "message:old"), pending.map { it.stableKey })
        assertEquals(listOf("todo:todo-1", "message:new", "message:old"), completed.map { it.stableKey })
        assertTrue(completed.any { it is ConversationTimelineItem.TodoCard && it.todo.status == TodoState.COMPLETED })
    }

    @Test
    fun `database timeline sequence orders a todo after its triggering user message`() {
        val user =
            ConversationMessage(
                "user",
                "Create a task",
                id = "user",
                createdAtEpochMillis = 2_000,
                timelineSequence = 41,
            )
        val todo =
            TodoItem(
                "todo-3",
                "Created from the request",
                createdAt = Instant.ofEpochMilli(2_000),
                updatedAt = Instant.ofEpochMilli(2_000),
                timelineSequence = 42,
            )

        val items = buildConversationTimeline(listOf(user), null, "", false, false, false, todos = listOf(todo))

        assertEquals(listOf("todo:todo-3", "message:user"), items.map { it.stableKey })
    }

    @Test
    fun `legacy equal timestamps place todo activity on the newer side deterministically`() {
        val user = ConversationMessage("user", "Create a task", id = "user", createdAtEpochMillis = 2_000)
        val todo = TodoItem("todo-4", "Legacy task", createdAt = Instant.ofEpochMilli(2_000))

        val items = buildConversationTimeline(listOf(user), null, "", false, false, false, todos = listOf(todo))

        assertEquals(listOf("todo:todo-4", "message:user"), items.map { it.stableKey })
    }

    @Test
    fun `deleted todo keeps a stable unavailable card snapshot`() {
        val todo = TodoItem("todo-2", "Removed task", createdAt = Instant.ofEpochMilli(2_000))
        val items =
            buildConversationTimeline(
                history = emptyList(),
                pendingUserMessage = null,
                streamingContent = "",
                showWaitingIndicator = false,
                showOlderHistoryLoading = false,
                includeInlineComposer = false,
                deletedTodoSnapshots = mapOf(todo.id to todo),
            )

        val card = items.single() as ConversationTimelineItem.TodoCard
        assertEquals("todo:todo-2", card.stableKey)
        assertTrue(card.deleted)
        assertEquals(todo.description, card.todo.description)
    }
}
