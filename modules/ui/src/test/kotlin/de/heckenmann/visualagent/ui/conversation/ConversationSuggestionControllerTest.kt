package de.heckenmann.visualagent.ui.conversation

import de.heckenmann.visualagent.protocol.CancellationToken
import de.heckenmann.visualagent.protocol.ConversationCompletionEvent
import de.heckenmann.visualagent.protocol.ConversationSuggestionPort
import de.heckenmann.visualagent.protocol.ConversationSuggestionRequest
import de.heckenmann.visualagent.protocol.ConversationSuggestionResult
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** Verifies turn correlation and cancellation rules for idle suggestion generation. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationSuggestionControllerTest {
    private val assistantId = "22222222-2222-4222-8222-222222222222"

    @Test
    fun `completed turn requests suggestions after eligibility delay`() =
        runTest {
            val port = RecordingSuggestionPort()
            val controller = ConversationSuggestionController(port, backgroundScope, pause = {})
            controller.updateSettings(SettingsSnapshot(followUpSuggestionIdleDelaySeconds = 3, followUpSuggestionCount = 3))

            controller.onCompletion(ConversationCompletionEvent(assistantId, 4))
            runCurrent()

            assertEquals(1, port.requests.size)
            assertEquals(assistantId, port.requests.single().assistantEntryId)
            controller.close()
        }

    @Test
    fun `focus that existed before completion does not block suggestions`() =
        runTest {
            val port = RecordingSuggestionPort()
            val controller = ConversationSuggestionController(port, backgroundScope, pause = {})

            controller.updateSettings(SettingsSnapshot())
            controller.onFocusChanged(true)
            controller.onCompletion(ConversationCompletionEvent(assistantId, 4))
            runCurrent()

            assertEquals(1, port.requests.size)
            controller.close()
        }

    @Test
    fun `focus gained after completion does not cancel an empty suggestion`() =
        runTest {
            val port = RecordingSuggestionPort()
            val controller = ConversationSuggestionController(port, backgroundScope, pause = {})

            controller.updateSettings(SettingsSnapshot())
            controller.onCompletion(ConversationCompletionEvent(assistantId, 4))
            controller.onFocusChanged(true)
            runCurrent()

            assertEquals(1, port.requests.size)
            controller.close()
        }

    @Test
    fun `typing clears a suggestion even when the composer remains focused`() =
        runTest {
            val port =
                RecordingSuggestionPort(
                    result =
                        listOf(
                            "What should we explore next?",
                        ),
                )
            val controller = ConversationSuggestionController(port, backgroundScope, pause = {})

            controller.updateSettings(SettingsSnapshot())
            controller.onFocusChanged(true)
            controller.onCompletion(ConversationCompletionEvent(assistantId, 4))
            runCurrent()
            controller.onInputChanged("W")

            assertEquals(ConversationSuggestionPhase.IDLE, controller.state.value.phase)
            controller.close()
        }

    @Test
    fun `user interaction consumes turn and prevents a late request`() =
        runTest {
            val port = RecordingSuggestionPort()
            val controller = ConversationSuggestionController(port, backgroundScope, pause = {})

            controller.updateSettings(SettingsSnapshot())
            controller.onCompletion(ConversationCompletionEvent(assistantId, 4))
            controller.onUserInteraction()
            runCurrent()

            assertEquals(0, port.requests.size)
            assertEquals(ConversationSuggestionPhase.IDLE, controller.state.value.phase)
            controller.close()
        }

    @Test
    fun `sending and queue state delay an eligible completion until both are clear`() =
        runTest {
            val port = RecordingSuggestionPort()
            val controller = ConversationSuggestionController(port, backgroundScope, pause = {})

            controller.updateSettings(SettingsSnapshot())
            controller.onSendingChanged(true)
            controller.onQueueSizeChanged(1)
            controller.onCompletion(ConversationCompletionEvent(assistantId, 4))
            runCurrent()
            assertEquals(0, port.requests.size)

            controller.onSendingChanged(false)
            runCurrent()
            assertEquals(0, port.requests.size)

            controller.onQueueSizeChanged(0)
            runCurrent()
            assertEquals(1, port.requests.size)
            controller.close()
        }

    @Test
    fun `mismatched result identity is discarded`() =
        runTest {
            val port = RecordingSuggestionPort(resultId = "33333333-3333-4333-8333-333333333333")
            val controller = ConversationSuggestionController(port, backgroundScope, pause = {})

            controller.updateSettings(SettingsSnapshot())
            controller.onCompletion(ConversationCompletionEvent(assistantId, 4))
            runCurrent()

            assertEquals(ConversationSuggestionPhase.IDLE, controller.state.value.phase)
            controller.close()
        }

    @Test
    fun `does not request suggestions before settings are loaded`() =
        runTest {
            val port = RecordingSuggestionPort()
            val controller = ConversationSuggestionController(port, backgroundScope, pause = {})

            controller.onCompletion(ConversationCompletionEvent(assistantId, 4))
            runCurrent()

            assertEquals(0, port.requests.size)
            controller.close()
        }

    @Test
    fun `does not retry an empty result for the same completed turn`() =
        runTest {
            val port = RecordingSuggestionPort()
            val controller = ConversationSuggestionController(port, backgroundScope, pause = {})
            controller.updateSettings(SettingsSnapshot())

            controller.onCompletion(ConversationCompletionEvent(assistantId, 4))
            runCurrent()
            controller.onFocusChanged(true)
            controller.updateSettings(SettingsSnapshot())
            runCurrent()

            assertEquals(1, port.requests.size)
            controller.close()
        }

    private class RecordingSuggestionPort(
        private val resultId: String? = null,
        private val result: List<String> = emptyList(),
    ) : ConversationSuggestionPort {
        val requests = mutableListOf<ConversationSuggestionRequest>()

        override suspend fun generate(
            request: ConversationSuggestionRequest,
            token: CancellationToken,
        ): ConversationSuggestionResult {
            requests += request
            return ConversationSuggestionResult(resultId ?: request.assistantEntryId, result)
        }

        override fun addCompletionListener(listener: (ConversationCompletionEvent) -> Unit): AutoCloseable = AutoCloseable { }
    }
}
