package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.conversation.ResponseTelemetryMetadata
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@de.heckenmann.visualagent.testsupport.DatabaseTest
class AgentManagerStructuredResponseTest {
    @Test
    fun `stream message persists accumulated reasoning summaries only when enabled`() =
        runBlocking {
            val db =
                de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                    .create("jdbc:sqlite::memory:")
            val provider = mockk<LLMProvider>(relaxed = true)
            coEvery { provider.stream(any<ChatRequestContext>()) } returns
                flowOf(
                    ChatResponse(
                        model = "test",
                        message = Message("assistant", "answer"),
                        done = false,
                        providerTurn = ProviderTurnResponse("test", "answer", reasoning = "plan ", reasoningIsSummary = true),
                    ),
                    ChatResponse(
                        model = "test",
                        message = Message("assistant", ""),
                        done = true,
                        providerTurn =
                            ProviderTurnResponse(
                                "test",
                                "",
                                reasoning = "complete",
                                reasoningIsSummary = true,
                                finishReason = ProviderFinishReason.STOP,
                            ),
                    ),
                )
            val config = AppConfigBean(db)
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), config)

            manager.streamMessage("hi") { }

            val telemetry = ResponseTelemetryMetadata.decode(manager.getHistory().last().metadata)
            assertEquals("plan complete", telemetry?.reasoning)
        }

    @Test
    fun `telemetry excludes raw provider reasoning`() {
        val telemetry =
            ResponseTelemetryMetadata.decode(
                ResponseTelemetryMetadata.encode(
                    ProviderTurnResponse("test", "answer", reasoning = "raw provider thinking"),
                    includeReasoning = true,
                ),
            )

        assertNull(telemetry?.reasoning)
    }

    @Test
    fun `stream message clears telemetry after a repetition retry`() =
        runBlocking {
            val db =
                de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                    .create("jdbc:sqlite::memory:")
            val provider = mockk<LLMProvider>(relaxed = true)
            coEvery { provider.stream(any<ChatRequestContext>()) } returns
                flowOf(
                    ChatResponse(
                        model = "test",
                        message = Message("assistant", "repeated response ".repeat(30)),
                        done = true,
                        providerTurn = ProviderTurnResponse("test", "", finishReason = ProviderFinishReason.STOP),
                    ),
                )
            coEvery { provider.chat(any<ChatRequestContext>()) } returns
                ChatResponse(model = "test", message = Message("assistant", "replacement"), done = true)
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))

            assertEquals("replacement", manager.streamMessage("hi") { })
            assertNull(ResponseTelemetryMetadata.decode(manager.getHistory().last().metadata))
        }
}
