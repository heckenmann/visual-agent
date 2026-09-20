package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.conversation.ConversationCompletionEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.ConversationRecord
import de.heckenmann.visualagent.knowledge.ConversationStore
import de.heckenmann.visualagent.protocol.CancellationTokenImpl
import de.heckenmann.visualagent.protocol.ConversationSuggestionRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator
import reactor.core.publisher.Mono
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
            every { provider.chatReactive(any<ChatRequestContext>()) } returns Mono.just(validResponse())

            val result = port.generate(ConversationSuggestionRequest(assistantId), CancellationTokenImpl())

            assertEquals(
                listOf("What should we explore next?", "Which risk deserves attention?", "How would you validate this?"),
                result.questions,
            )
            verify(exactly = 1) {
                provider.chatReactive(
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
            verify(exactly = 0) { provider.chatReactive(any<ChatRequestContext>()) }
        }

    @Test
    fun `strips reasoning markup before sending context to a provider`() =
        runTest {
            configureEnabled()
            every { store.getConversationMessages("main", 500) } returns
                history().map { row ->
                    if (row.id == assistantId) row.copy(content = "Visible answer.<think>private reasoning</think>") else row
                }
            val request = slot<ChatRequestContext>()
            every { provider.chatReactive(capture(request)) } returns Mono.just(validResponse())

            port.generate(ConversationSuggestionRequest(assistantId), CancellationTokenImpl())

            assertFalse(request.captured.messages.any { "<think>" in it.content || "private reasoning" in it.content })
        }

    @Test
    fun `bounds long suggestion context to the configured context length`() =
        runTest {
            configureEnabled(contextLength = 1_024)
            every { store.getConversationMessages("main", 500) } returns longHistory()
            val request = slot<ChatRequestContext>()
            every { provider.chatReactive(capture(request)) } returns Mono.just(validResponse())

            port.generate(ConversationSuggestionRequest(assistantId), CancellationTokenImpl())

            assertTrue(request.captured.messages.any { it.role == "assistant" })
            assertTrue(
                JTokkitTokenCountEstimator().estimate(request.captured.messages.joinToString("\n") { it.content }) <= 1_024,
            )
        }

    @Test
    fun `disabled setting skips database and provider work`() =
        runTest {
            every { config.followUpSuggestionsEnabled } returns false
            every { config.followUpSuggestionCount } returns 3

            val result = port.generate(ConversationSuggestionRequest(assistantId), CancellationTokenImpl())

            assertEquals(emptyList(), result.questions)
            verify(exactly = 0) { provider.chatReactive(any<ChatRequestContext>()) }
        }

    private fun configureEnabled(contextLength: Int = 4_096) {
        every { config.followUpSuggestionsEnabled } returns true
        every { config.followUpSuggestionCount } returns 3
        every { config.contextLength } returns contextLength
    }

    private fun validResponse(): ChatResponse =
        ChatResponse(
            model = "active-model",
            message =
                Message(
                    "assistant",
                    "[\"What should we explore next?\",\"Which risk deserves attention?\",\"How would you validate this?\"]",
                ),
            done = true,
        )

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

    private fun longHistory(): List<ConversationRecord> =
        buildList {
            repeat(5) { index ->
                add(
                    ConversationRecord(
                        id = "00000000-0000-4000-8000-00000000000${index + 1}",
                        role = "user",
                        content = "question ".repeat(8_000),
                        metadata = null,
                        createdAt = Instant.EPOCH,
                        timelineSequence = (index * 2 + 1).toLong(),
                    ),
                )
                add(
                    ConversationRecord(
                        id = if (index == 4) assistantId else "10000000-0000-4000-8000-00000000000${index + 1}",
                        role = "assistant",
                        content = "answer ".repeat(8_000),
                        metadata = null,
                        createdAt = Instant.EPOCH,
                        timelineSequence = (index * 2 + 2).toLong(),
                    ),
                )
            }
        }
}
