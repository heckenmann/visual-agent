package de.heckenmann.visualagent.protocol

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/** Immutable conversation message transferred across the UI/server boundary. */
data class ConversationMessage(
    val role: String,
    val content: String,
    val metadata: String? = null,
    val images: List<String>? = null,
    val id: String? = null,
)

/** Page of persisted conversation messages returned by the server. */
data class ConversationHistoryPage(
    val messages: List<ConversationMessage>,
    val offset: Int,
    val hasMore: Boolean,
)

/** Cancellation handle that is safe to expose to a presentation client. */
interface CancellationToken {
    /** Whether cancellation has already been requested. */
    val isCancelled: Boolean

    /** Requests cancellation of the associated server operation. */
    fun cancel()

    /** Registers a callback invoked once cancellation is requested. */
    fun onCancelled(listener: () -> Unit)
}

/** Default thread-safe cancellation handle for local and remote UI clients. */
class CancellationTokenImpl : CancellationToken {
    private val cancelled = AtomicBoolean(false)
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    override val isCancelled: Boolean
        get() = cancelled.get()

    override fun cancel() {
        if (!cancelled.compareAndSet(false, true)) return
        listeners.forEach { listener -> runCatching(listener) }
        listeners.clear()
    }

    override fun onCancelled(listener: () -> Unit) {
        if (isCancelled) {
            runCatching(listener)
        } else {
            listeners += listener
            if (isCancelled && listeners.remove(listener)) runCatching(listener)
        }
    }
}

/** Transport-neutral conversation operations used by the Compose conversation panel. */
interface ConversationPort {
    /** Reads the newest persisted conversation page. */
    suspend fun latest(): ConversationHistoryPage

    /** Reads an older persisted conversation page. */
    suspend fun older(offset: Int): ConversationHistoryPage

    /** Streams one assistant response and invokes [onChunk] for each text delta. */
    suspend fun stream(
        content: String,
        token: CancellationToken,
        onChunk: (String) -> Unit,
    )

    /** Returns the currently visible conversation history. */
    fun currentHistory(): List<ConversationMessage>

    /** Removes one persisted message and reports whether it existed. */
    fun deleteMessage(id: String): Boolean

    /** Replaces one persisted message and reports whether it existed. */
    fun updateMessage(
        id: String,
        content: String,
    ): Boolean

    /** Cancels active application work before clearing persisted conversation history. */
    fun cancelActiveWork()

    /** Clears history and creates a fresh welcome message. */
    suspend fun clearAndCreateWelcome(): ConversationClearResult

    /** Reads presentation preferences needed by the conversation panel. */
    fun preferences(): ConversationPreferences

    /** Persists presentation preferences needed by the conversation panel. */
    fun updatePreferences(preferences: ConversationPreferences)
}

/** Result of clearing a conversation and composing its new welcome message. */
data class ConversationClearResult(
    val warning: String? = null,
)

/** Persisted conversation-panel preferences exposed through the protocol boundary. */
data class ConversationPreferences(
    val inputPlacement: ConversationInputPlacement = ConversationInputPlacement.CONVERSATION_MESSAGE,
    val queueFlushMode: String = "ONE_BY_ONE",
)

/** Controls where the conversation composer is rendered. */
enum class ConversationInputPlacement {
    /** Keep the composer at the bottom of the conversation panel. */
    FIXED,

    /** Render the composer as a message-like card at the latest conversation end. */
    CONVERSATION_MESSAGE,
}
