package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.provider.ProviderUserFacingError
import de.heckenmann.visualagent.agent.provider.ProviderUserFacingException
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.protocol.ConversationCompletionEvent
import de.heckenmann.visualagent.protocol.ConversationCompletionEventBus
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@de.heckenmann.visualagent.testsupport.DatabaseTest
class AgentManagerStreamingConversationTest {
    @Test
    fun `stream message emits chunks and persists assistant response`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
            val provider = mockk<LLMProvider>(relaxed = true)
            coEvery { provider.stream(any<ChatRequestContext>()) } returns
                flowOf(
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
            val chunks = mutableListOf<String>()

            val result = manager.streamMessage("hi", onChunk = { chunks += it }, userEntryId = USER_ID, assistantEntryId = ASSISTANT_ID)

            assertEquals("Hello world", result)
            assertEquals(listOf("Hello", " world"), chunks)
            val history = manager.getHistory()
            assertEquals("user", history.first().role)
            assertEquals("assistant", history.last().role)
            assertEquals(listOf(USER_ID, ASSISTANT_ID), history.mapNotNull(Message::id))
            assertEquals(listOf(ASSISTANT_ID), events.map(ConversationCompletionEvent::assistantEntryId))
        }

    @Test
    fun `transport retry reuses the persisted assistant identity without another provider call`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
            val provider = mockk<LLMProvider>(relaxed = true)
            coEvery { provider.stream(any<ChatRequestContext>()) } returns
                flowOf(ChatResponse(model = "test", message = Message("assistant", "Answer"), done = true))
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))

            manager.streamMessage("Request", onChunk = {}, userEntryId = USER_ID, assistantEntryId = ASSISTANT_ID)
            val retryChunks = mutableListOf<String>()
            manager.streamMessage("Request", onChunk = retryChunks::add, userEntryId = USER_ID, assistantEntryId = ASSISTANT_ID)

            assertEquals(listOf("Answer"), retryChunks)
            assertEquals(listOf(USER_ID, ASSISTANT_ID), manager.getHistory().mapNotNull(Message::id))
            coVerify(exactly = 1) { provider.stream(any<ChatRequestContext>()) }
        }

    @Test
    fun `transport retry rejects an assistant identity linked to another user entry`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
            val provider = mockk<LLMProvider>(relaxed = true)
            coEvery { provider.stream(any<ChatRequestContext>()) } returns
                flowOf(ChatResponse(model = "test", message = Message("assistant", "Answer"), done = true))
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))

            manager.streamMessage("Request", onChunk = {}, userEntryId = USER_ID, assistantEntryId = ASSISTANT_ID)

            assertFailsWith<IllegalArgumentException> {
                manager.streamMessage("Request", onChunk = {}, userEntryId = SECOND_USER_ID, assistantEntryId = ASSISTANT_ID)
            }
            assertFailsWith<IllegalArgumentException> {
                manager.streamMessage("Request", onChunk = {}, userEntryId = SECOND_USER_ID, assistantEntryId = USER_ID)
            }

            coVerify(exactly = 1) { provider.stream(any<ChatRequestContext>()) }
        }

    @Test
    fun `stream message persists a safe provider failure response`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
            val provider = mockk<LLMProvider>(relaxed = true)
            coEvery { provider.stream(any<ChatRequestContext>()) } returns
                flow {
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
    fun `cancelled partial stream does not publish a suggestion completion`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
            val provider = mockk<LLMProvider>(relaxed = true)
            val token = CancellationToken()
            val events = mutableListOf<ConversationCompletionEvent>()
            val completionEvents = ConversationCompletionEventBus()
            completionEvents.addListener(events::add)
            coEvery { provider.stream(any<ChatRequestContext>()) } returns
                flow {
                    emit(ChatResponse(model = "test", message = Message("assistant", "partial"), done = false))
                    token.cancel()
                    emit(ChatResponse(model = "test", message = Message("assistant", "ignored"), done = true))
                }
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

            manager.streamMessage("hi", token, onChunk = {}, userEntryId = USER_ID, assistantEntryId = ASSISTANT_ID)

            assertEquals(emptyList(), events)
        }

    @Test
    fun `stream message preserves adjacent sentence chunks`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
            val provider = mockk<LLMProvider>(relaxed = true)
            coEvery { provider.stream(any<ChatRequestContext>()) } returns
                flowOf(
                    ChatResponse(model = "test", message = Message("assistant", "First."), done = false),
                    ChatResponse(model = "test", message = Message("assistant", "Second."), done = true),
                )
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))
            val chunks = mutableListOf<String>()

            val result = manager.streamMessage("hi", onChunk = { chunks += it }, userEntryId = USER_ID, assistantEntryId = ASSISTANT_ID)

            assertEquals("First.Second.", result)
            assertEquals(listOf("First.", "Second."), chunks)
            assertEquals("First.Second.", manager.getHistory().last().content)
        }

    @Test
    fun `stream message persists thinking markup but removes it from provider history`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
            val provider = mockk<LLMProvider>(relaxed = true)
            coEvery { provider.stream(any<ChatRequestContext>()) } returns
                flowOf(
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
    }
}
