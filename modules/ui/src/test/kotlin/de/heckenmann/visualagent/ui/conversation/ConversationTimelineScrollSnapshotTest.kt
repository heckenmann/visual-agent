package de.heckenmann.visualagent.ui.conversation

import de.heckenmann.visualagent.protocol.ConversationMessage
import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.ui.todo.TodoResponseState
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies activity detection independently of layout timing and presentation grouping. */
class ConversationTimelineScrollSnapshotTest {
    @Test
    fun `todo inserted behind an existing streaming row counts as new activity`() {
        val stream = ConversationTimelineItem.Streaming("Working", "stream")
        val message = persisted("existing")
        val previous = snapshot(stream, message)
        val todo = ConversationTimelineItem.TodoCard(TodoItem("todo", "New todo"), TodoResponseState(), false)
        assertTrue(snapshot(stream, todo, message).hasNewContentSince(previous))
    }

    @Test
    fun `streaming and review updates on the same todo response holder are observed`() {
        val response = TodoResponseState()
        val todo = ConversationTimelineItem.TodoCard(TodoItem("todo", "Work"), response, false)
        val initial = snapshot(todo)
        response.apply("execution", "worker", "First chunk", false)
        val streaming = snapshot(todo)
        assertTrue(streaming.hasNewContentSince(initial))
        response.apply("execution", "worker", "", false, reviewing = true)
        val reviewing = snapshot(todo)
        assertTrue(reviewing.hasNewContentSince(streaming))
        response.apply("execution", "worker", "", true)
        assertTrue(snapshot(todo).hasNewContentSince(reviewing))
    }

    @Test
    fun `older pages and regrouped user runs do not count as new activity`() {
        val newest = persisted("newest", role = "user", index = 0)
        val previous = snapshot(ConversationTimelineItem.InlineComposer, newest)
        val group =
            ConversationTimelineItem.PersistedGroup(
                ConversationMessageGroup(listOf(newest.copy(chronologicalIndex = 1), persisted("older", role = "user"))),
            )
        assertFalse(
            snapshot(
                ConversationTimelineItem.InlineComposer,
                group,
                ConversationTimelineItem.OlderHistoryLoading,
            ).hasNewContentSince(previous),
        )
    }

    @Test
    fun `new structured tool results on a known parent are observed`() {
        val parent = persisted("parent", role = "assistant")
        val older = persisted("older")
        val previous = snapshot(ConversationTimelineItem.PersistedGroup(ConversationMessageGroup(listOf(parent))), older)
        val group = ConversationTimelineItem.PersistedGroup(ConversationMessageGroup(listOf(parent, persisted("tool", role = "tool"))))
        assertTrue(snapshot(group, older).hasNewContentSince(previous))
    }

    @Test
    fun `new global activity sequence is observed even at the end of a grouped turn`() {
        val parent = persisted("parent").let { it.copy(message = it.message.copy(timelineSequence = 1)) }
        val previous = snapshot(ConversationTimelineItem.PersistedGroup(ConversationMessageGroup(listOf(parent))))
        val result = persisted("tool", role = "tool").let { it.copy(message = it.message.copy(timelineSequence = 2)) }
        val group = ConversationTimelineItem.PersistedGroup(ConversationMessageGroup(listOf(parent, result)))
        assertTrue(snapshot(group).hasNewContentSince(previous))
    }

    @Test
    fun `unchanged content and composer placement alone do not count as new activity`() {
        val item = persisted("message")
        val previous = snapshot(item)
        assertFalse(snapshot(ConversationTimelineItem.InlineComposer, item.copy(chronologicalIndex = 10)).hasNewContentSince(previous))
    }

    private fun persisted(
        id: String,
        role: String = "assistant",
        index: Int = 0,
    ) = ConversationTimelineItem.Persisted(ConversationMessage(role, id, id = id), index)

    private fun snapshot(vararg items: ConversationTimelineItem) = conversationTimelineScrollSnapshot(items.toList())
}
