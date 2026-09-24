package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.conversation.ConversationCompletionEventBus
import de.heckenmann.visualagent.agent.provider.ProviderUserFacingError
import de.heckenmann.visualagent.agent.provider.ProviderUserFacingException
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.protocol.ConversationCompletionEvent
import de.heckenmann.visualagent.protocol.ConversationStreamUpdate
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.reactor.flux
import kotlinx.coroutines.runBlocking
import reactor.core.publisher.Flux
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@de.heckenmann.visualagent.testsupport.DatabaseTest
class AgentManagerStreamingConversationTest {
    @Test
    fun `follow-up request includes the previous assistant Markdown response from the database`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:h2:mem:test")
            val provider = mockk<LLMProvider>(relaxed = true)
            val requests = mutableListOf<ChatRequestContext>()
            val previousMarkdown =
                listOf(
                    "```markdown",
                    "# Heading",
                    "- First",
                    "- Second",
                    "**Done**",
                    "```",
                ).joinToString("\n")
            every { provider.streamReactive(any<ChatRequestContext>()) } answers {
                requests += firstArg<ChatRequestContext>()
                val content = if (requests.size == 1) previousMarkdown else "This explains the snippet."
                Flux.just(ChatResponse(model = "test", message = Message("assistant", content), done = true))
            }
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))

            manager.streamMessage(
                "Create a five-line Markdown example",
                onChunk = {},
                userEntryId = USER_ID,
                assistantEntryId = ASSISTANT_ID,
            )
            manager.streamMessage(
                "Explain that",
                onChunk = {},
                userEntryId = SECOND_USER_ID,
                assistantEntryId = THIRD_ASSISTANT_ID,
            )

            val followUpContext = requests.last().messages
            assertEquals(
                listOf(
                    "Create a five-line Markdown example",
                    previousMarkdown,
                    "Explain that",
                ),
                followUpContext.filter { it.role == "user" || it.role == "assistant" }.takeLast(3).map { it.content },
            )
            db.close()
        }

    @Test
    fun `stream message emits chunks and persists assistant response`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:h2:mem:test")
            val provider = mockk<LLMProvider>(relaxed = true)
            every { provider.streamReactive(any<ChatRequestContext>()) } returns
                Flux.just(
                    ChatResponse(model = "test", message = Message("assistant", "Hello"), done = false),
                    ChatResponse(model = "test", message = Message("assistant", " world"), done = true),
                )
            val events = mutableListOf<ConversationCompletionEvent>()
            val completionEvents = ConversationCompletionEventBus()
            completionEvents.addListener(events::add)
            val manager =
                AgentManager(
                    db,
                    provider,
                    AgentToolConfigService(db),
                    ToolEventBus(),
                    TodoEventBus(),
                    AppConfigBean(db),
                    conversationCompletionEvents = completionEvents,
                )
            val chunks = mutableListOf<ConversationStreamUpdate>()

            val result = manager.streamMessage("hi", onChunk = { chunks += it }, userEntryId = USER_ID, assistantEntryId = ASSISTANT_ID)

            assertEquals("Hello world", result)
            assertEquals(listOf("Hello", " world"), chunks.map(ConversationStreamUpdate::textDelta))
            val history = manager.getHistory()
            assertEquals("user", history.first().role)
            assertEquals("assistant", history.last().role)
            assertEquals(listOf(USER_ID, ASSISTANT_ID), history.mapNotNull(Message::id))
            assertEquals(listOf(ASSISTANT_ID), events.map(ConversationCompletionEvent::assistantEntryId))
        }

    @Test
    fun `transport retry reuses the persisted assistant identity without another provider call`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:h2:mem:test")
            val provider = mockk<LLMProvider>(relaxed = true)
            every { provider.streamReactive(any<ChatRequestContext>()) } returns
                Flux.just(ChatResponse(model = "test", message = Message("assistant", "Answer"), done = true))
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))

            manager.streamMessage("Request", onChunk = {}, userEntryId = USER_ID, assistantEntryId = ASSISTANT_ID)
            val retryChunks = mutableListOf<ConversationStreamUpdate>()
            manager.streamMessage("Request", onChunk = retryChunks::add, userEntryId = USER_ID, assistantEntryId = ASSISTANT_ID)

            assertEquals(listOf("Answer"), retryChunks.map(ConversationStreamUpdate::textDelta))
            assertEquals(listOf(USER_ID, ASSISTANT_ID), manager.getHistory().mapNotNull(Message::id))
            verify(exactly = 1) { provider.streamReactive(any<ChatRequestContext>()) }
        }

    @Test
    fun `transport retry rejects an assistant identity linked to another user entry`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:h2:mem:test")
            val provider = mockk<LLMProvider>(relaxed = true)
            every { provider.streamReactive(any<ChatRequestContext>()) } returns
                Flux.just(ChatResponse(model = "test", message = Message("assistant", "Answer"), done = true))
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))

            manager.streamMessage("Request", onChunk = {}, userEntryId = USER_ID, assistantEntryId = ASSISTANT_ID)

            assertFailsWith<IllegalArgumentException> {
                manager.streamMessage("Request", onChunk = {}, userEntryId = SECOND_USER_ID, assistantEntryId = ASSISTANT_ID)
            }
            assertFailsWith<IllegalArgumentException> {
                manager.streamMessage("Request", onChunk = {}, userEntryId = SECOND_USER_ID, assistantEntryId = USER_ID)
            }

            verify(exactly = 1) { provider.streamReactive(any<ChatRequestContext>()) }
        }

    @Test
    fun `stream message persists a safe provider failure response`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:h2:mem:test")
            val provider = mockk<LLMProvider>(relaxed = true)
            every { provider.streamReactive(any<ChatRequestContext>()) } returns
                flux {
                    throw ProviderUserFacingException(
                        ProviderUserFacingError(
                            "Provider executable unavailable",
                            "The required provider executable is not installed.",
                            false,
                        ),
                    )
                }
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))

            val result = manager.streamMessage("hi", onChunk = {}, userEntryId = USER_ID, assistantEntryId = ASSISTANT_ID)

            assertEquals("Provider executable unavailable\n\nThe required provider executable is not installed.", result)
            assertEquals(listOf("user", "assistant"), manager.getHistory().map(Message::role))
            assertEquals(result, manager.getHistory().last().content)
            assertEquals(ASSISTANT_ID, manager.getHistory().last().id)
        }

    @Test
    fun `stream message preserves adjacent sentence chunks`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:h2:mem:test")
            val provider = mockk<LLMProvider>(relaxed = true)
            every { provider.streamReactive(any<ChatRequestContext>()) } returns
                Flux.just(
                    ChatResponse(model = "test", message = Message("assistant", "First."), done = false),
                    ChatResponse(model = "test", message = Message("assistant", "Second."), done = true),
                )
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))
            val chunks = mutableListOf<ConversationStreamUpdate>()

            val result = manager.streamMessage("hi", onChunk = { chunks += it }, userEntryId = USER_ID, assistantEntryId = ASSISTANT_ID)

            assertEquals("First.Second.", result)
            assertEquals(listOf("First.", "Second."), chunks.map(ConversationStreamUpdate::textDelta))
            assertEquals("First.Second.", manager.getHistory().last().content)
        }

    @Test
    fun `streaming keeps assistant tool rounds as separate stable messages`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:h2:mem:test")
            val provider = mockk<LLMProvider>(relaxed = true)
            every { provider.streamReactive(any<ChatRequestContext>()) } returns
                Flux.just(
                    ChatResponse(
                        model = "test",
                        message = Message("assistant", "I'll inspect the file."),
                        done = false,
                        providerTurn =
                            ProviderTurnResponse(
                                model = "test",
                                content = "I'll inspect the file.",
                                toolCalls = listOf(ProviderToolCall("call-1", "function", "file_read", "{}")),
                                metadata = ProviderResponseMetadata(requestId = ASSISTANT_ID, round = 0),
                            ),
                    ),
                    ChatResponse(
                        model = "test",
                        message = Message("assistant", "The file is valid."),
                        done = true,
                        providerTurn =
                            ProviderTurnResponse(
                                model = "test",
                                content = "The file is valid.",
                                metadata = ProviderResponseMetadata(requestId = ASSISTANT_ID, round = 1),
                            ),
                    ),
                )
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))
            val updates = mutableListOf<ConversationStreamUpdate>()

            manager.streamMessage("Check this file", onChunk = updates::add, userEntryId = USER_ID, assistantEntryId = ASSISTANT_ID)
            val retryUpdates = mutableListOf<ConversationStreamUpdate>()
            manager.streamMessage("Check this file", onChunk = retryUpdates::add, userEntryId = USER_ID, assistantEntryId = ASSISTANT_ID)

            val assistantTurns = manager.getHistory().filter { it.role == "assistant" }
            assertEquals(listOf("I'll inspect the file.", "The file is valid."), assistantTurns.map(Message::content))
            assertEquals(
                listOf(ASSISTANT_ID, AssistantTurnIdentity.forRound(ASSISTANT_ID, 1)),
                assistantTurns.mapNotNull(Message::id),
            )
            assertEquals(
                listOf(ASSISTANT_ID, AssistantTurnIdentity.forRound(ASSISTANT_ID, 1)),
                updates.map(ConversationStreamUpdate::assistantTurnId),
            )
            assertEquals(
                updates.map(ConversationStreamUpdate::assistantTurnId),
                retryUpdates.map(ConversationStreamUpdate::assistantTurnId),
            )
            assertEquals(updates.map(ConversationStreamUpdate::textDelta), retryUpdates.map(ConversationStreamUpdate::textDelta))
            assertTrue(assistantTurns.all { it.conversationRequestId == ASSISTANT_ID })
            verify(exactly = 1) { provider.streamReactive(any<ChatRequestContext>()) }
        }

    @Test
    fun `tool-only streamed turn remains empty without placeholder prose`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:h2:mem:test")
            val provider = mockk<LLMProvider>(relaxed = true)
            every { provider.streamReactive(any<ChatRequestContext>()) } returns
                Flux.just(
                    ChatResponse(
                        model = "test",
                        message = Message("assistant", ""),
                        done = false,
                        providerTurn =
                            ProviderTurnResponse(
                                model = "test",
                                content = "",
                                toolCalls = listOf(ProviderToolCall("call-1", "function", "network_dns", "{}")),
                                metadata = ProviderResponseMetadata(requestId = ASSISTANT_ID, round = 0),
                            ),
                    ),
                    ChatResponse(
                        model = "test",
                        message = Message("assistant", "DNS lookup completed."),
                        done = true,
                        providerTurn =
                            ProviderTurnResponse(
                                model = "test",
                                content = "DNS lookup completed.",
                                metadata = ProviderResponseMetadata(requestId = ASSISTANT_ID, round = 1),
                            ),
                    ),
                )
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))

            manager.streamMessage("Resolve the hostname", onChunk = {}, userEntryId = USER_ID, assistantEntryId = ASSISTANT_ID)

            val assistantTurns = manager.getHistory().filter { it.role == "assistant" }
            assertEquals("", assistantTurns.first().content)
            assertTrue(assistantTurns.first().assistantToolTurn)
            assertEquals("DNS lookup completed.", assistantTurns.last().content)
            assertFalse(assistantTurns.any { it.content == "(No text response. See tool results above.)" })
        }

    @Test
    fun `stream message persists thinking markup but removes it from provider history`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:h2:mem:test")
            val provider = mockk<LLMProvider>(relaxed = true)
            every { provider.streamReactive(any<ChatRequestContext>()) } returns
                Flux.just(
                    ChatResponse(model = "test", message = Message("assistant", "<think>first</think>"), done = false),
                    ChatResponse(model = "test", message = Message("assistant", "<think>second</think>answer"), done = true),
                )
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))

            assertEquals(
                "answer",
                manager.streamMessage("hi", onChunk = {}, userEntryId = USER_ID, assistantEntryId = ASSISTANT_ID),
            )
            assertEquals("<think>first</think><think>second</think>answer", manager.getHistory().last().content)
            val providerHistory = manager.conversationOps.buildMainRequest(manager.conversationOps.loadRecentHistoryFromDb())
            assertEquals("answer", providerHistory.messages.last().content)
        }

    private companion object {
        const val USER_ID = "11111111-1111-4111-8111-111111111111"
        const val ASSISTANT_ID = "22222222-2222-4222-8222-222222222222"
        const val SECOND_USER_ID = "33333333-3333-4333-8333-333333333333"
        const val THIRD_ASSISTANT_ID = "44444444-4444-4444-8444-444444444444"
    }
}
