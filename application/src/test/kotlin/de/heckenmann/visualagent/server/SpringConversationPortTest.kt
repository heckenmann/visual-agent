package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ProviderTurnResponse
import de.heckenmann.visualagent.agent.clearTodos
import de.heckenmann.visualagent.agent.conversation.ConversationHistoryPage
import de.heckenmann.visualagent.agent.conversation.ResponseTelemetryMetadata
import de.heckenmann.visualagent.agent.conversation.WelcomeResult
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.protocol.CancellationTokenImpl
import de.heckenmann.visualagent.protocol.ConversationImageResolution
import de.heckenmann.visualagent.protocol.ConversationInputPlacement
import de.heckenmann.visualagent.protocol.ConversationPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Verifies mapping and cancellation at the Spring-to-protocol conversation seam. */
class SpringConversationPortTest {
    private val manager = mockk<AgentManager>()
    private val mediaResolver = mockk<ConversationMediaResolver>(relaxed = true)
    private val port = SpringConversationPort(manager, AppConfigBean(), mediaResolver)

    @Test
    fun `latest page maps application messages to protocol messages`() =
        runTest {
            every { manager.readLatestHistoryPage() } returns
                ConversationHistoryPage(
                    messages = listOf(Message("assistant", "ready", id = "m1")),
                    offset = 0,
                    hasMore = false,
                )

            val page = port.latest()

            assertEquals("assistant", page.messages.single().role)
            assertEquals("ready", page.messages.single().content)
            assertEquals("m1", page.messages.single().id)
        }

    @Test
    fun `latest page exposes structured provider reasoning separately from content`() =
        runTest {
            every { manager.readLatestHistoryPage() } returns
                ConversationHistoryPage(
                    messages =
                        listOf(
                            Message(
                                "assistant",
                                "answer",
                                metadata =
                                    ResponseTelemetryMetadata.encode(
                                        ProviderTurnResponse(
                                            model = "model",
                                            content = "answer",
                                            reasoning = "planning",
                                            reasoningIsSummary = true,
                                        ),
                                        includeReasoning = true,
                                    ),
                            ),
                        ),
                    offset = 0,
                    hasMore = false,
                )

            val page = port.latest()

            assertEquals("answer", page.messages.single().content)
            assertEquals("planning", page.messages.single().reasoning)
        }

    @Test
    fun `canvas data url is exposed as a validated protocol image`() =
        runTest {
            val dataUrl = "data:image/png;base64,AAAA"
            every { manager.getHistory() } returns
                listOf(
                    Message(
                        role = "assistant",
                        content = "Canvas snapshot (PNG)",
                        metadata = "{\"type\":\"image\",\"source\":\"canvas\",\"dataUrl\":\"$dataUrl\"}",
                    ),
                )
            every { manager.loadLatestHistory() } returns emptyList()
            every { mediaResolver.resolveEmbedded(dataUrl) } returns
                ConversationImageResolution.Loaded("image/png", byteArrayOf(1, 2, 3))

            val message = port.currentHistory().single()

            assertNotNull(message.images)
            assertEquals(1, message.images!!.size)
            assertEquals("data:image/png;base64,AQID", message.images!!.single())
        }

    @Test
    fun `current history reloads messages persisted by tools`() =
        runTest {
            val persistedCapture = Message("assistant", "Canvas snapshot (PNG)", id = "capture")
            every { manager.loadLatestHistory() } returns listOf(persistedCapture)
            every { manager.getHistory() } returns listOf(persistedCapture)

            val history = port.currentHistory()

            assertEquals("capture", history.single().id)
            verify(exactly = 1) { manager.loadLatestHistory() }
        }

    @Test
    fun `non-object or non-primitive image metadata does not break history mapping`() =
        runTest {
            every { manager.loadLatestHistory() } returns emptyList()
            every { manager.getHistory() } returns
                listOf(
                    Message("assistant", "array metadata", metadata = "[]"),
                    Message("assistant", "nested data url", metadata = "{\"dataUrl\":{\"value\":\"x\"}}"),
                )

            val history = port.currentHistory()

            assertEquals(2, history.size)
            assertEquals(null, history[0].images)
            assertEquals(null, history[1].images)
        }

    @Test
    fun `stream cancellation is bridged to application token`() =
        runTest {
            coEvery { manager.streamMessage(any(), any(), any(), any(), any()) } coAnswers {
                thirdArg<(String) -> Unit>().invoke("delta")
                "delta"
            }
            val token = CancellationTokenImpl()
            val chunks = mutableListOf<String>()

            port.stream("hello", token) { chunks += it }
            token.cancel()

            assertEquals(listOf("delta"), chunks)
            coVerify(exactly = 1) { manager.streamMessage("hello", any(), any(), any(), any()) }
        }

    @Test
    fun `message edits and cancellation are delegated to the application`() {
        every { manager.deleteMessageById("message-1") } returns Unit
        every { manager.updateMessageContentById("message-1", "updated") } returns Unit
        every { manager.cancelAllRunningActions() } returns emptySet()
        every { manager.cancelAllActiveTodos() } returns Unit

        assertEquals(true, port.deleteMessage("message-1"))
        assertEquals(true, port.updateMessage("message-1", "updated"))
        port.cancelActiveWork()

        verify(exactly = 1) { manager.deleteMessageById("message-1") }
        verify(exactly = 1) { manager.updateMessageContentById("message-1", "updated") }
        verify(exactly = 1) { manager.cancelAllRunningActions() }
        verify(exactly = 1) { manager.cancelAllActiveTodos() }
    }

    @Test
    fun `conversation preferences are mapped without exposing application enums`() {
        val config = AppConfigBean()
        config.conversationInputPlacement = de.heckenmann.visualagent.config.ConversationInputPlacement.FIXED
        config.queueFlushMode = "ALL"
        val configPort = SpringConversationPort(manager, config, mediaResolver)

        assertEquals(ConversationInputPlacement.FIXED, configPort.preferences().inputPlacement)
        assertEquals("ALL", configPort.preferences().queueFlushMode)

        configPort.updatePreferences(
            ConversationPreferences(
                inputPlacement = ConversationInputPlacement.CONVERSATION_MESSAGE,
                queueFlushMode = "ONE_BY_ONE",
            ),
        )
        assertEquals(
            de.heckenmann.visualagent.config.ConversationInputPlacement.CONVERSATION_MESSAGE,
            config.conversationInputPlacement,
        )
        assertEquals("ONE_BY_ONE", config.queueFlushMode)
    }

    @Test
    fun `welcome fallback is returned as a protocol warning`() =
        runTest {
            every { manager.clearTodos() } returns Unit
            every { manager.clearHistory() } returns Unit
            coEvery { manager.addWelcomeMessageAfterReset() } returns
                WelcomeResult.Fallback("fallback", IllegalStateException("Provider not reachable"))

            val result = port.clearAndCreateWelcome()

            assertEquals(
                "The provider could not be reached. Check the connection and provider base URL.",
                result.warning,
            )
            verify(exactly = 1) { manager.clearTodos() }
            verify(exactly = 1) { manager.clearHistory() }
            verifyOrder {
                manager.clearTodos()
                manager.clearHistory()
            }
        }
}
