@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.flow.MutableStateFlow
import de.heckenmann.visualagent.protocol.ConversationHistoryPage as ConversationHistoryPage
import de.heckenmann.visualagent.protocol.ConversationMessage as Message

internal data class ConversationHistoryRequest(
    val generation: Long,
    val offset: Int,
)

@Stable
internal class ConversationUiState(
    initialHistory: List<Message>,
) {
    var history: List<Message> by mutableStateOf(initialHistory.distinctPersistedMessages())
        private set
    var input by mutableStateOf("")
    var status by mutableStateOf("Ready")
    var sending by mutableStateOf(false)
    var editingId: String? by mutableStateOf(null)
    var deletingMessageIds: Set<String> by mutableStateOf(emptySet())
    var pendingUserMessage: String? by mutableStateOf(null)
    var pendingUserEntryId: String? by mutableStateOf(null)
    var streamingEntryId: String? by mutableStateOf(null)
    var isLoadingOlder by mutableStateOf(false)
        private set
    var hasMoreHistory by mutableStateOf(true)
        private set
    val streaming = MutableStateFlow("")

    private var historyGeneration = 0L
    private var reachedOldestHistory = false

    /**
     * Tracks entries that have already been composed once.
     *
     * This is deliberately not Compose state: marking a row as known happens from its first
     * composition and must not trigger a second composition while its enter transition starts.
     */
    private val knownEntryIds = initialHistory.mapNotNull(Message::id).toMutableSet()

    fun replaceHistory(messages: List<Message>) {
        historyGeneration++
        history = messages.distinctPersistedMessages()
        markEntriesKnown(history)
        isLoadingOlder = false
        hasMoreHistory = history.isNotEmpty()
        reachedOldestHistory = false
    }

    /** Atomically replaces transient entries with their persisted counterparts after one stream completes. */
    fun completeStream(messages: List<Message>) {
        historyGeneration++
        history = messages.distinctPersistedMessages()
        pendingUserMessage = null
        pendingUserEntryId = null
        streamingEntryId = null
        streaming.value = ""
        isLoadingOlder = false
        hasMoreHistory = history.isNotEmpty()
        reachedOldestHistory = false
    }

    fun beginLatestRequest(): ConversationHistoryRequest {
        historyGeneration++
        isLoadingOlder = false
        return ConversationHistoryRequest(historyGeneration, 0)
    }

    fun applyLatest(
        request: ConversationHistoryRequest,
        page: ConversationHistoryPage,
    ): Boolean {
        if (request.generation != historyGeneration || request.offset != 0) return false
        val latestMessages = page.messages.distinctPersistedMessages()
        val latestIds = latestMessages.mapNotNull(Message::id).toSet()
        val retainedHistory = history.filter { it.id == null || it.id !in latestIds }
        history = retainedHistory + latestMessages
        if (!reachedOldestHistory) {
            hasMoreHistory = page.hasMore
        }
        return true
    }

    fun beginOlderRequest(): ConversationHistoryRequest? {
        if (history.isEmpty() || isLoadingOlder || !hasMoreHistory) return null
        isLoadingOlder = true
        return ConversationHistoryRequest(historyGeneration, history.size)
    }

    fun applyOlder(
        request: ConversationHistoryRequest,
        page: ConversationHistoryPage,
    ): Int {
        if (request.generation != historyGeneration || request.offset != page.offset) return 0
        val existingIds = history.mapNotNull(Message::id).toSet()
        val older = page.messages.distinctPersistedMessages().filter { it.id == null || it.id !in existingIds }
        if (older.isNotEmpty()) {
            history = older.toList() + history
            markEntriesKnown(older)
        }
        hasMoreHistory = page.hasMore && older.isNotEmpty()
        reachedOldestHistory = !hasMoreHistory
        return older.size
    }

    fun finishOlderRequest(request: ConversationHistoryRequest) {
        if (request.generation == historyGeneration) {
            isLoadingOlder = false
        }
    }

    /** Clears transient state and treats the replacement history as already known after a full reset. */
    fun resetHistory(messages: List<Message>) {
        knownEntryIds.clear()
        replaceHistory(messages)
        pendingUserMessage = null
        pendingUserEntryId = null
        streamingEntryId = null
        streaming.value = ""
    }

    /** Returns whether this entry should play the new-message animation on first composition. */
    fun shouldAnimateEntry(id: String?): Boolean = id != null && id !in knownEntryIds

    /** Marks a rendered entry as known without changing its stable identity. */
    fun markEntryKnown(id: String?) {
        if (id != null) knownEntryIds += id
    }

    private fun markEntriesKnown(messages: List<Message>) {
        knownEntryIds += messages.mapNotNull(Message::id)
    }
}

internal fun List<Message>.distinctPersistedMessages(): List<Message> {
    val seenIds = mutableSetOf<String>()
    return asReversed()
        .filter { message -> message.id?.let(seenIds::add) ?: true }
        .asReversed()
}

@Composable
internal fun rememberConversationUiState(initialHistory: List<Message>): ConversationUiState =
    androidx.compose.runtime.remember { ConversationUiState(initialHistory) }
