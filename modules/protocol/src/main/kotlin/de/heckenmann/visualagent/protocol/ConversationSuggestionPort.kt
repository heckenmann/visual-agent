package de.heckenmann.visualagent.protocol

import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Request for server-generated follow-up questions for one completed assistant turn. */
data class ConversationSuggestionRequest(
    val assistantEntryId: String,
    val previousQuestions: List<String> = emptyList(),
) {
    init {
        requireCanonicalUuid(assistantEntryId)
        require(previousQuestions.size <= MAX_SUGGESTION_COUNT) { "At most $MAX_SUGGESTION_COUNT previous questions are supported" }
        require(previousQuestions.all { it.length <= MAX_QUESTION_LENGTH }) { "Previous questions exceed the supported length" }
    }
}

/** Result of a suggestion request; an empty list means that no safe suggestions are available. */
data class ConversationSuggestionResult(
    val assistantEntryId: String,
    val questions: List<String>,
)

/** Notification emitted after a successful assistant response has been persisted. */
data class ConversationCompletionEvent(
    val assistantEntryId: String,
    val timelineSequence: Long?,
)

/** Server-owned operation for generating non-persistent follow-up question inspiration. */
interface ConversationSuggestionPort {
    /** Generates validated follow-up questions without exposing tools to the provider. */
    suspend fun generate(
        request: ConversationSuggestionRequest,
        token: CancellationToken,
    ): ConversationSuggestionResult

    /** Registers a listener for successful persisted assistant responses. */
    fun addCompletionListener(listener: (ConversationCompletionEvent) -> Unit): AutoCloseable
}

/** Maximum number of questions accepted in one suggestion batch. */
const val MAX_SUGGESTION_COUNT: Int = 5

/** Maximum number of characters accepted in one suggestion. */
const val MAX_QUESTION_LENGTH: Int = 140

private fun requireCanonicalUuid(value: String) {
    val canonical = runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
    require(canonical) { "Assistant entry ID must be a canonical UUID" }
}

/** Thread-safe event source used by the local server adapter. */
class ConversationCompletionEventBus {
    private val listeners = CopyOnWriteArrayList<(ConversationCompletionEvent) -> Unit>()

    /** Publishes one completion event to all currently registered listeners. */
    fun publish(event: ConversationCompletionEvent) {
        listeners.forEach { listener -> runCatching { listener(event) } }
    }

    /** Publishes a completion using the supplied assistant entry ID. */
    fun publishCompletion(assistantEntryId: String) {
        publish(ConversationCompletionEvent(assistantEntryId, null))
    }

    /** Registers a listener and returns a handle that removes it. */
    fun addListener(listener: (ConversationCompletionEvent) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }
}
