@file:Suppress("ktlint:standard:no-wildcard-imports")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.ui.focus.FocusRequester
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.PreferenceStore
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.ui.agents.*
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.canvas.*
import de.heckenmann.visualagent.ui.components.*
import de.heckenmann.visualagent.ui.conversation.*
import de.heckenmann.visualagent.ui.files.*
import de.heckenmann.visualagent.ui.modal.*
import de.heckenmann.visualagent.ui.settings.*
import de.heckenmann.visualagent.ui.status.*
import de.heckenmann.visualagent.ui.todo.*
import de.heckenmann.visualagent.ui.workspace.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposeConversationPanelActionsTest {
    @Test
    fun `conversation placement persistence runs off the caller thread`() =
        runBlocking {
            val caller = Thread.currentThread()
            val persistenceThreads = CopyOnWriteArrayList<Thread>()
            val store =
                object : PreferenceStore {
                    override fun getPreference(key: String): String? = null

                    override fun setPreference(
                        key: String,
                        value: String,
                    ) {
                        persistenceThreads += Thread.currentThread()
                    }
                }

            persistConversationInputPlacement(AppConfigBean(store))

            assertTrue(persistenceThreads.isNotEmpty())
            assertTrue(persistenceThreads.all { it != caller })
        }

    @Test
    fun `executeSend streams message and updates state`() =
        runBlocking {
            val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
            val provider = mockk<de.heckenmann.visualagent.agent.LLMProvider>(relaxed = true)
            val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus(), AppConfigBean(db))
            val inFlight = InFlightStateHolder()
            val focusRequester = FocusRequester()
            var input = ""
            var sending = false
            var status = ""
            var history = emptyList<Message>()
            var activeToken: CancellationToken? = null
            val streamingFlow = MutableStateFlow("")

            val mockManager = mockk<AgentManager>(relaxed = true)
            every { mockManager.getHistory() } returns emptyList()
            coEvery { mockManager.streamMessage(any(), any(), any()) } answers {
                val onChunk = thirdArg<(String) -> Unit>()
                onChunk("Hello")
                onChunk(" world")
                "Hello world"
            }

            executeSend(
                content = "Test",
                agentManager = mockManager,
                inFlight = inFlight,
                inputFocusRequester = focusRequester,
                onInputChange = { input = it },
                onSendingChange = { sending = it },
                onStatusChange = { status = it },
                onHistoryChange = { history = it },
                onActiveTokenChange = { activeToken = it },
                onPendingUserMessageChange = {},
                streamingFlow = streamingFlow,
            )

            assertEquals("", input)
            assertEquals(false, sending)
            assertEquals("Ready", status)
            assertNull(activeToken)
            assertEquals(0, inFlight.state.value.totalActive)
        }

    @Test
    fun `executeSend handles failure`() =
        runBlocking {
            val inFlight = InFlightStateHolder()
            val focusRequester = FocusRequester()
            var sending = false
            var status = ""
            var history = emptyList<Message>()
            var activeToken: CancellationToken? = null
            val streamingFlow = MutableStateFlow("")

            val mockManager = mockk<AgentManager>(relaxed = true)
            every { mockManager.getHistory() } returns emptyList()
            coEvery { mockManager.streamMessage(any(), any(), any()) } throws RuntimeException("Boom")

            executeSend(
                content = "Test",
                agentManager = mockManager,
                inFlight = inFlight,
                inputFocusRequester = focusRequester,
                onInputChange = {},
                onSendingChange = { sending = it },
                onStatusChange = { status = it },
                onHistoryChange = { history = it },
                onActiveTokenChange = { activeToken = it },
                onPendingUserMessageChange = {},
                streamingFlow = streamingFlow,
            )

            assertEquals(false, sending)
            assertTrue(status.isNotBlank())
            assertEquals(0, inFlight.state.value.totalActive)
        }

    @Test
    fun `executeSend clears in-flight state after cancellation`() =
        runBlocking {
            val inFlight = InFlightStateHolder()
            val mockManager = mockk<AgentManager>(relaxed = true)
            every { mockManager.getHistory() } returns emptyList()
            coEvery { mockManager.streamMessage(any(), any(), any()) } throws CancellationException("Cancelled")

            executeSend(
                content = "Test",
                agentManager = mockManager,
                inFlight = inFlight,
                inputFocusRequester = FocusRequester(),
                onInputChange = {},
                onSendingChange = {},
                onStatusChange = {},
                onHistoryChange = {},
                onActiveTokenChange = {},
                onPendingUserMessageChange = {},
                streamingFlow = MutableStateFlow(""),
            )

            assertEquals(0, inFlight.state.value.totalActive)
        }
}
