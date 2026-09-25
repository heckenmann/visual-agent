package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.protocol.ConversationStreamUpdate
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import reactor.core.publisher.Flux
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@de.heckenmann.visualagent.testsupport.DatabaseTest
class AgentManagerStreamingToolRoundTest {
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

    private companion object {
        const val USER_ID = "11111111-1111-4111-8111-111111111111"
        const val ASSISTANT_ID = "22222222-2222-4222-8222-222222222222"
    }
}
