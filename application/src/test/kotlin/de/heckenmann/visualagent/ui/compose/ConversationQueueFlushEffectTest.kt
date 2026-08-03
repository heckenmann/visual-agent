@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ConversationQueueFlushEffectTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `queue is flushed when totalActive drops to zero while not sending`(): Unit =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
            val provider = mockk<de.heckenmann.visualagent.agent.LLMProvider>(relaxed = true)
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))
            val inFlight = InFlightStateHolder()
            val queue = MessageQueue()
            val focusRequester = FocusRequester()
            var sentMessages = 0

            val mockManager = mockk<AgentManager>(relaxed = true)
            every { mockManager.getHistory() } returns emptyList()
            coEvery { mockManager.streamMessage(any(), any(), any()) } answers {
                sentMessages++
                val onChunk = thirdArg<(String) -> Unit>()
                onChunk("ok")
                "ok"
            }

            composeTestRule.setContent {
                MaterialTheme {
                    ConversationQueueFlushEffect(
                        sending = false,
                        inFlight = inFlight,
                        queue = queue,
                        agentManager = mockManager,
                        inputFocusRequester = focusRequester,
                        onInputChange = {},
                        onSendingChange = {},
                        onStatusChange = {},
                        onHistoryChange = {},
                        onActiveTokenChange = {},
                        onPendingUserMessageChange = {},
                        streamingFlow = MutableStateFlow(""),
                    )
                }
            }
            composeTestRule.waitForIdle()

            // Simulate an active agent: messages enqueued while work is in flight should wait.
            inFlight.markAgentStart("agent-1")
            composeTestRule.waitForIdle()

            queue.enqueue("first", QueuedMessageSource.USER)
            queue.enqueue("second", QueuedMessageSource.USER)
            composeTestRule.waitForIdle()

            assertEquals(0, sentMessages, "messages should not be sent while an agent is active")

            // When the agent finishes and totalActive drops to zero, the queue should flush.
            inFlight.markAgentEnd("agent-1")
            composeTestRule.waitUntil(5_000) { sentMessages == 2 && queue.size == 0 }

            assertEquals(2, sentMessages, "both queued messages should be flushed when totalActive drops to zero")
            assertEquals(0, queue.size, "queue should be empty after flush")
        }

    @Test
    fun `queue is flushed after executeSend sets sending back to false`(): Unit =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
            val provider = mockk<de.heckenmann.visualagent.agent.LLMProvider>(relaxed = true)
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))
            val inFlight = InFlightStateHolder()
            val queue = MessageQueue()
            val focusRequester = FocusRequester()
            var sending = false
            var sentMessages = 0

            val mockManager = mockk<AgentManager>(relaxed = true)
            every { mockManager.getHistory() } returns emptyList()
            coEvery { mockManager.streamMessage(any(), any(), any()) } answers {
                sentMessages++
                val onChunk = thirdArg<(String) -> Unit>()
                onChunk("ok")
                "ok"
            }

            composeTestRule.setContent {
                MaterialTheme {
                    ConversationQueueFlushEffect(
                        sending = sending,
                        inFlight = inFlight,
                        queue = queue,
                        agentManager = mockManager,
                        inputFocusRequester = focusRequester,
                        onInputChange = {},
                        onSendingChange = { sending = it },
                        onStatusChange = {},
                        onHistoryChange = {},
                        onActiveTokenChange = {},
                        onPendingUserMessageChange = {},
                        streamingFlow = MutableStateFlow(""),
                    )
                }
            }
            composeTestRule.waitForIdle()

            // Start a send: this sets sending=true and adds a stream id to inFlight.
            queue.enqueue("queued while busy", QueuedMessageSource.USER)
            inFlight.markStreamStart("stream-1")
            sending = true
            composeTestRule.waitForIdle()

            assertEquals(0, sentMessages, "queued message should wait while a stream is active")

            // Simulate executeSend finishing: stream ends, sending=false.
            inFlight.markStreamEnd("stream-1")
            sending = false
            composeTestRule.waitUntil(5_000) { sentMessages == 1 && queue.size == 0 }

            assertEquals(1, sentMessages, "queued message should be flushed after the active send finishes")
            assertEquals(0, queue.size, "queue should be empty after flush")
        }
}
