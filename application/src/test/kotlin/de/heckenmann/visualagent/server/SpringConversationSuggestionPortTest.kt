package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.ConversationRecord
import de.heckenmann.visualagent.knowledge.ConversationStore
import de.heckenmann.visualagent.protocol.CancellationTokenImpl
import de.heckenmann.visualagent.protocol.ConversationCompletionEventBus
import de.heckenmann.visualagent.protocol.ConversationSuggestionRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

/** Verifies bounded, tool-less server-side suggestion generation. */
class SpringConversationSuggestionPortTest {
    private val assistantId = "22222222-2222-4222-8222-222222222222"
    private val store = mockk<ConversationStore>()
    private val provider = mockk<LLMProvider>()
    private val config = mockk<AppConfigBean>()
    private val port =
        SpringConversationSuggestionPort(
            provider = provider,
            conversationStore = store,
            appConfig = config,
            completionEvents = ConversationCompletionEventBus(),
        )

    @Test
    fun `generates exact validated count with no tools and bounded context`() =
        runTest {
            configureEnabled()
            every { store.getConversationMessages("main", 500) } returns history()
            coEvery { provider.chat(any<ChatRequestContext>()) } returns
                ChatResponse(
                    model = "active-model",
                    message =
                        Message(
                            "assistant",
                            "[\"What should we explore next?\",\"Which risk deserves attention?\",\"How would you validate this?\"]",
                        ),
                    done = true,
                )

            val result = port.generate(ConversationSuggestionRequest(assistantId), CancellationTokenImpl())

            assertEquals(
                listOf("What should we explore next?", "Which risk deserves attention?", "How would you validate this?"),
                result.questions,
            )
            coVerify(exactly = 1) {
                provider.chat(
                    match<ChatRequestContext> {
                        it.enabledTools.isEmpty() &&
                            it.parameters.maxTokens == 256 &&
                            it.messages.size == 4 &&
                            it.metadata["agent"] == "conversation-suggestions"
                    },
                )
            }
        }

    @Test
    fun `stale assistant anchor is rejected before provider access`() =
        runTest {
            configureEnabled()
            every { store.getConversationMessages("main", 500) } returns
                history().map { if (it.id == assistantId) it.copy(id = "33333333-3333-4333-8333-333333333333") else it }

            val result = port.generate(ConversationSuggestionRequest(assistantId), CancellationTokenImpl())

            assertEquals(emptyList(), result.questions)
            coVerify(exactly = 0) { provider.chat(any<ChatRequestContext>()) }
        }

    @Test
    fun `disabled setting skips database and provider work`() =
        runTest {
            every { config.followUpSuggestionsEnabled } returns false
            every { config.followUpSuggestionCount } returns 3

            val result = port.generate(ConversationSuggestionRequest(assistantId), CancellationTokenImpl())

            assertEquals(emptyList(), result.questions)
            coVerify(exactly = 0) { provider.chat(any<ChatRequestContext>()) }
        }

    private fun configureEnabled() {
        every { config.followUpSuggestionsEnabled } returns true
        every { config.followUpSuggestionCount } returns 3
    }

    private fun history(): List<ConversationRecord> =
        listOf(
            ConversationRecord(
                id = "11111111-1111-4111-8111-111111111111",
                role = "user",
                content = "Explain the release process.",
                metadata = null,
                createdAt = Instant.EPOCH,
                timelineSequence = 1,
            ),
            ConversationRecord(
                id = assistantId,
                role = "assistant",
                content = "The release process uses staged validation.",
                metadata = null,
                createdAt = Instant.EPOCH,
                timelineSequence = 2,
            ),
        )
}
