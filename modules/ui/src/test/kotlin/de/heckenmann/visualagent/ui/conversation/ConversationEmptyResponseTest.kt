package de.heckenmann.visualagent.ui.conversation

import de.heckenmann.visualagent.protocol.ConversationMessage
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConversationEmptyResponseTest {
    @Test
    fun `blank final answer has no bubble while its structural parent and tools remain visible`() {
        val parent = ConversationMessage("assistant", "", id = "parent", assistantToolTurn = true)
        val tool = ConversationMessage("tool", "Tool result", id = "tool", parentAssistantTurnId = "parent", turnOrder = 0)
        val final = ConversationMessage("assistant", "", id = "final")
        val items = timeline(listOf(parent, tool, final))
        assertEquals(1, items.size)
        assertEquals(listOf(parent, tool), (items.single() as ConversationTimelineItem.PersistedGroup).group.messages.map { it.message })
    }

    @Test
    fun `image-only assistant answers are not hidden`() {
        val message = ConversationMessage("assistant", "", images = listOf("workspace-id:picture"), id = "image")
        assertEquals(message, (timeline(listOf(message)).single() as ConversationTimelineItem.MessageEntry).message)
        assertTrue(timeline(listOf(ConversationMessage("assistant", " ", id = "empty"))).isEmpty())
    }

    private fun timeline(history: List<ConversationMessage>) =
        buildConversationTimeline(
            history = history,
            pendingUserMessage = null,
            streamingContent = "",
            requestActive = false,
            showOlderHistoryLoading = false,
            includeInlineComposer = false,
        )
}
