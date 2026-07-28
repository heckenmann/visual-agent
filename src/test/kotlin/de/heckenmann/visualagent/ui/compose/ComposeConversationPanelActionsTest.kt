package de.heckenmann.visualagent.ui.compose

import androidx.compose.ui.focus.FocusRequester
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.Message
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ComposeConversationPanelActionsTest {
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
            assertNotNull(activeToken)
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
        }
}
