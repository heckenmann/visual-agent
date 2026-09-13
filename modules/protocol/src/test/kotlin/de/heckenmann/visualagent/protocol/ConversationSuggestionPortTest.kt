package de.heckenmann.visualagent.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Verifies validation and delivery guarantees of the follow-up suggestion protocol. */
class ConversationSuggestionPortTest {
    private val assistantId = "22222222-2222-4222-8222-222222222222"

    @Test
    fun `request accepts canonical id and bounded previous questions`() {
        val request =
            ConversationSuggestionRequest(
                assistantEntryId = assistantId,
                previousQuestions = listOf("What should we explore next?"),
            )

        assertEquals(assistantId, request.assistantEntryId)
        assertEquals(1, request.previousQuestions.size)
    }

    @Test
    fun `request rejects malformed ids and oversized previous questions`() {
        assertFailsWith<IllegalArgumentException> {
            ConversationSuggestionRequest("assistant:$assistantId")
        }
        assertFailsWith<IllegalArgumentException> {
            ConversationSuggestionRequest(assistantId, List(MAX_SUGGESTION_COUNT + 1) { "Question?" })
        }
        assertFailsWith<IllegalArgumentException> {
            ConversationSuggestionRequest(assistantId, listOf("x".repeat(MAX_QUESTION_LENGTH + 1)))
        }
    }

    @Test
    fun `completion bus notifies listeners and removes them`() {
        val bus = ConversationCompletionEventBus()
        val received = mutableListOf<ConversationCompletionEvent>()
        val registration = bus.addListener(received::add)
        val event = ConversationCompletionEvent(assistantId, 17)

        bus.publish(event)
        registration.close()
        bus.publish(ConversationCompletionEvent(assistantId, 18))

        assertEquals(listOf(event), received)
    }
}
