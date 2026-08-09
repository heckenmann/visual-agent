@file:Suppress("ktlint:standard:no-wildcard-imports")

package de.heckenmann.visualagent.ui.conversation

import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.conversation.ConversationHistoryPage
import de.heckenmann.visualagent.ui.agents.*
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.canvas.*
import de.heckenmann.visualagent.ui.components.*
import de.heckenmann.visualagent.ui.conversation.*
import de.heckenmann.visualagent.ui.files.*
import de.heckenmann.visualagent.ui.modal.*
import de.heckenmann.visualagent.ui.settings.*
import de.heckenmann.visualagent.ui.status.*
import de.heckenmann.visualagent.ui.todo.*
import de.heckenmann.visualagent.ui.workspace.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationUiStateTest {
    @Test
    fun `latest generation rejects an older page already in flight`() {
        val state = ConversationUiState(listOf(message("recent-1")))
        val olderRequest = state.beginOlderRequest()!!
        val latestRequest = state.beginLatestRequest()

        assertTrue(
            state.applyLatest(latestRequest, ConversationHistoryPage(listOf(message("recent-2")), offset = 0, hasMore = true)),
        )
        assertEquals(
            0,
            state.applyOlder(olderRequest, ConversationHistoryPage(listOf(message("older-1")), offset = 1, hasMore = true)),
        )
        assertEquals(listOf("recent-2"), state.history.map { it.id })
    }

    @Test
    fun `latest page disarms paging until viewport leaves the oldest end`() {
        val state = ConversationUiState(listOf(message("recent-1")))
        val latestRequest = state.beginLatestRequest()
        state.applyLatest(latestRequest, ConversationHistoryPage(listOf(message("recent-2")), offset = 0, hasMore = true))

        assertNull(state.beginOlderRequest())
        state.updateOldestPosition(false)
        assertEquals(1, state.beginOlderRequest()!!.offset)
    }

    @Test
    fun `older pages prepend unique messages and publish exhaustion`() {
        val state = ConversationUiState(listOf(message("recent-1")))
        val request = state.beginOlderRequest()!!

        val added =
            state.applyOlder(
                request,
                ConversationHistoryPage(
                    listOf(message("older-1"), message("recent-1")),
                    offset = 1,
                    hasMore = false,
                ),
            )
        state.finishOlderRequest(request)

        assertEquals(1, added)
        assertEquals(listOf("older-1", "recent-1"), state.history.map { it.id })
        assertFalse(state.hasMoreHistory)
        assertFalse(state.isLoadingOlder)
    }

    @Test
    fun `timeline has explicit stable order from newest to oldest`() {
        val items =
            buildConversationTimeline(
                history = listOf(message("oldest"), message("newest")),
                pendingUserMessage = "pending",
                streamingContent = "streaming",
                showWaitingIndicator = true,
                showOlderHistoryLoading = true,
                includeInlineComposer = true,
            )

        assertIs<ConversationTimelineItem.InlineComposer>(items[0])
        assertIs<ConversationTimelineItem.Waiting>(items[1])
        assertIs<ConversationTimelineItem.Streaming>(items[2])
        assertIs<ConversationTimelineItem.PendingUser>(items[3])
        assertEquals("message:newest", items[4].stableKey)
        assertEquals("message:oldest", items[5].stableKey)
        assertIs<ConversationTimelineItem.OlderHistoryLoading>(items[6])
        assertEquals(items.size, items.map { it.stableKey }.distinct().size)
    }

    private fun message(id: String): Message = Message("user", id, id = id)
}
