@file:Suppress("ktlint:standard:no-wildcard-imports")

package de.heckenmann.visualagent.ui.conversation

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
import kotlin.test.assertTrue
import de.heckenmann.visualagent.protocol.ConversationHistoryPage as ConversationHistoryPage
import de.heckenmann.visualagent.protocol.ConversationMessage as Message

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
        assertEquals(listOf("recent-1", "recent-2"), state.history.map { it.id })
    }

    @Test
    fun `latest page keeps older history paging available for the next traversal`() {
        val state = ConversationUiState(listOf(message("recent-1")))
        val latestRequest = state.beginLatestRequest()
        state.applyLatest(latestRequest, ConversationHistoryPage(listOf(message("recent-2")), offset = 0, hasMore = true))

        assertEquals(2, state.beginOlderRequest()!!.offset)
    }

    @Test
    fun `history replacement resets the oldest-page exhaustion state`() {
        val state = ConversationUiState(listOf(message("recent-1")))
        val request = state.beginOlderRequest()!!
        state.applyOlder(request, ConversationHistoryPage(emptyList(), offset = 1, hasMore = false))

        state.replaceHistory(listOf(message("recent-2")))

        assertEquals(1, state.beginOlderRequest()!!.offset)
    }

    @Test
    fun `latest page preserves already loaded older history`() {
        val state = ConversationUiState(listOf(message("oldest"), message("recent-1")))
        val request = state.beginLatestRequest()

        state.applyLatest(request, ConversationHistoryPage(listOf(message("recent-1"), message("recent-2")), 0, true))

        assertEquals(listOf("oldest", "recent-1", "recent-2"), state.history.map { it.id })
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
    fun `history boundaries keep only the newest occurrence of a persisted message`() {
        val state = ConversationUiState(listOf(message("duplicate", "stale"), message("duplicate", "initial")))

        state.replaceHistory(listOf(message("duplicate", "old"), message("duplicate", "replacement")))
        val request = state.beginLatestRequest()
        state.applyLatest(
            request,
            ConversationHistoryPage(
                listOf(message("duplicate", "page-old"), message("duplicate", "page-new")),
                offset = 0,
                hasMore = false,
            ),
        )

        assertEquals(listOf("page-new"), state.history.map { it.content })
    }

    @Test
    fun `timeline emits a unique key when history contains a repeated persisted id`() {
        val items =
            buildConversationTimeline(
                history = listOf(message("duplicate", "stale"), message("duplicate", "newest")),
                pendingUserMessage = null,
                streamingContent = "",
                showWaitingIndicator = false,
                showOlderHistoryLoading = false,
                includeInlineComposer = false,
            )

        assertEquals(listOf("message:duplicate"), items.map { it.stableKey })
        val group = (items.single() as ConversationTimelineItem.PersistedGroup).group
        assertEquals(
            "newest",
            group.messages
                .single()
                .message.content,
        )
    }

    @Test
    fun `timeline keeps the newest message group before older history`() {
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
        assertIs<ConversationTimelineItem.OlderHistoryLoading>(items[5])
        assertEquals(items.size, items.map { it.stableKey }.distinct().size)
    }

    private fun message(
        id: String,
        content: String = id,
    ): Message = Message("user", content, id = id)
}
