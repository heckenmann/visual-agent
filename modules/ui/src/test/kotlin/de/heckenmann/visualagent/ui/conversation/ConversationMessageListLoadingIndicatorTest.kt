package de.heckenmann.visualagent.ui.conversation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import de.heckenmann.visualagent.protocol.ConversationMessage as Message

/** Verifies in-flight requests no longer add a separate conversation status row. */
class ConversationMessageListLoadingIndicatorTest {
    @Test
    fun `active request has no separate thinking row`() {
        val timeline =
            buildConversationTimeline(
                history = listOf(Message("assistant", "Earlier response", id = "assistant-1")),
                pendingUserMessage = null,
                streamingContent = "",
                requestActive = true,
                showOlderHistoryLoading = false,
                includeInlineComposer = false,
            )

        assertEquals(1, timeline.size)
        assertIs<ConversationTimelineItem.MessageEntry>(timeline.single())
    }

    @Test
    fun `empty active conversation hides empty placeholder`() {
        val timeline =
            buildConversationTimeline(
                history = emptyList(),
                pendingUserMessage = null,
                streamingContent = "",
                requestActive = true,
                showOlderHistoryLoading = false,
                includeInlineComposer = false,
            )

        assertEquals(emptyList(), timeline)
    }

    @Test
    fun `empty inactive conversation still shows empty placeholder`() {
        val timeline =
            buildConversationTimeline(
                history = emptyList(),
                pendingUserMessage = null,
                streamingContent = "",
                requestActive = false,
                showOlderHistoryLoading = false,
                includeInlineComposer = false,
            )

        assertIs<ConversationTimelineItem.Empty>(timeline.single())
    }
}
