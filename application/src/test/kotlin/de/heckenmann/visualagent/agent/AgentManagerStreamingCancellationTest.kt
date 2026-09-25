package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.conversation.ConversationCompletionEventBus
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.protocol.ConversationCompletionEvent
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.reactor.flux
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies assistant-turn identity and side effects when a streamed response is cancelled. */
@de.heckenmann.visualagent.testsupport.DatabaseTest
class AgentManagerStreamingCancellationTest {
    @Test
    fun `cancelled partial stream does not publish a suggestion completion`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:h2:mem:test")
            val provider = mockk<LLMProvider>(relaxed = true)
            val token = CancellationToken()
            val events = mutableListOf<ConversationCompletionEvent>()
            val completionEvents = ConversationCompletionEventBus().also { it.addListener(events::add) }
            every { provider.streamReactive(any<ChatRequestContext>()) } returns
                flux {
                    send(ChatResponse(model = "test", message = Message("assistant", "partial"), done = false))
                    token.cancel()
                    send(ChatResponse(model = "test", message = Message("assistant", "ignored"), done = true))
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
            assertEquals(listOf("partial", "(cancelled)"), manager.getHistory().filter { it.role == "assistant" }.map(Message::content))
        }

    @Test
    fun `cancelled tool-only turn persists its parent and skips the follow-up request`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:h2:mem:test")
            val provider = mockk<LLMProvider>(relaxed = true)
            val token = CancellationToken()
            every { provider.streamReactive(any<ChatRequestContext>()) } returns
                flux {
                    send(
                        ChatResponse(
                            model = "test",
                            message = Message("assistant", ""),
                            done = false,
                            providerTurn =
                                ProviderTurnResponse(
                                    model = "test",
                                    content = "",
                                    toolCalls = listOf(ProviderToolCall("call-1", "function", "file_read", "{}")),
                                    metadata = ProviderResponseMetadata(requestId = ASSISTANT_ID, round = 0),
                                ),
                        ),
                    )
                    token.cancel()
                    send(ChatResponse(model = "test", message = Message("assistant", "ignored"), done = true))
                }
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))

            manager.streamMessage("Inspect a file", token, onChunk = {}, userEntryId = USER_ID, assistantEntryId = ASSISTANT_ID)

            val assistants = manager.getHistory().filter { it.role == "assistant" }
            assertTrue(assistants.first().assistantToolTurn)
            assertEquals(ASSISTANT_ID, assistants.first().id)
            assertEquals(ASSISTANT_ID, assistants.first().conversationRequestId)
            assertEquals("(cancelled)", assistants.last().content)
            assertEquals(ASSISTANT_ID, assistants.last().conversationRequestId)
            verify(exactly = 0) { provider.chatReactive(any<ChatRequestContext>()) }
        }

    private companion object {
        const val USER_ID = "11111111-1111-4111-8111-111111111111"
        const val ASSISTANT_ID = "22222222-2222-4222-8222-222222222222"
    }
}
