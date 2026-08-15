package de.heckenmann.visualagent.ui.conversation

import org.junit.Test
import kotlin.test.assertEquals
import de.heckenmann.visualagent.protocol.ConversationMessage as Message

/**
 * Verifies the presentation-only grouping rules for conversation messages.
 */
class ConversationMessageGroupTest {
    @Test
    fun `groups consecutive messages from the same conversational author`() {
        val groups =
            groupConsecutiveConversationMessages(
                listOf(
                    persisted("newest", "user"),
                    persisted("older", "user"),
                    persisted("assistant", "assistant"),
                ),
            )

        assertEquals(
            listOf(listOf("newest", "older"), listOf("assistant")),
            groups.map { group -> group.messages.map { it.message.content } },
        )
        assertEquals(listOf("user", "assistant"), groups.map(ConversationMessageGroup::role))
    }

    @Test
    fun `system and tool entries split otherwise matching author groups`() {
        val groups =
            groupConsecutiveConversationMessages(
                listOf(
                    persisted("latest", "user"),
                    persisted("tool status", "tool"),
                    persisted("before tool", "user"),
                    persisted("system notice", "system"),
                    persisted("before notice", "user"),
                ),
            )

        assertEquals(
            listOf(
                listOf("latest"),
                listOf("tool status"),
                listOf("before tool"),
                listOf("system notice"),
                listOf("before notice"),
            ),
            groups.map { group -> group.messages.map { it.message.content } },
        )
    }

    @Test
    fun `group keeps the newest message key so the reverse list remains newest first`() {
        val group =
            groupConsecutiveConversationMessages(
                listOf(persisted("newest", "user"), persisted("oldest", "user")),
            ).single()

        assertEquals("message:newest", group.stableKey)
    }

    private fun persisted(
        content: String,
        role: String,
    ): ConversationTimelineItem.Persisted = ConversationTimelineItem.Persisted(Message(role, content, id = content), 0)
}
