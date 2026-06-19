package de.heckenmann.visualagent.ui

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.ui.panels.ChatPanel
import de.heckenmann.visualagent.ui.panels.FxTestSupport
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MainWindowChatWiringTest {
    @Test
    fun `register wires todos history reset and non-streaming send`() =
        runBlocking {
            val fixture = fixture()
            val previousStreaming = AppConfig.instance.streamingEnabled
            val previousThinking = AppConfig.instance.thinkingEnabled
            try {
                AppConfig.instance.streamingEnabled = false
                AppConfig.instance.thinkingEnabled = true
                every { fixture.manager.clearHistory() } just Runs
                coEvery { fixture.manager.addWelcomeMessageAfterReset() } returns "Welcome"
                every { fixture.manager.loadOlderHistory(20) } returns listOf(Message("user", "older"))
                coEvery { fixture.manager.sendMessage("hello") } returns "<think>inspect</think>\nAnswer"

                fixture.wiring.register()
                fixture.openTodos.captured.invoke()
                fixture.loadOlder.captured.invoke()
                fixture.clear.captured.invoke()
                fixture.send.captured.invoke("hello")
                waitForUi()

                assertEquals(1, fixture.todoOpenCount)
                verify { fixture.chatPanel.prependConversationHistory(match { it.single().content == "older" }) }
                verify { fixture.chatPanel.startAssistantLoading() }
                verify { fixture.chatPanel.addAssistantMessage("Welcome") }
                verify { fixture.chatPanel.addThinkingEvent("inspect") }
                verify { fixture.chatPanel.addAssistantMessage("Answer") }
                verify { fixture.chatPanel.updateResponseMetrics(any()) }
            } finally {
                AppConfig.instance.streamingEnabled = previousStreaming
                AppConfig.instance.thinkingEnabled = previousThinking
                fixture.scope.cancel()
            }
        }

    @Test
    fun `streaming send updates partial and final messages`() =
        runBlocking {
            val fixture = fixture()
            val previousStreaming = AppConfig.instance.streamingEnabled
            try {
                AppConfig.instance.streamingEnabled = true
                coEvery { fixture.manager.streamMessage("stream", any()) } coAnswers {
                    val callback = arg<(String) -> Unit>(1)
                    callback("one")
                    callback(" two")
                    "one two"
                }
                fixture.wiring.register()

                fixture.send.captured.invoke("stream")
                waitForUi()

                verify { fixture.chatPanel.updateStreamingAssistantMessage("one") }
                verify { fixture.chatPanel.updateStreamingAssistantMessage("one two") }
                verify { fixture.chatPanel.finishStreamingAssistantMessage("one two") }
            } finally {
                AppConfig.instance.streamingEnabled = previousStreaming
            }
        }

    @Test
    fun `tool preview repairs blank response and provider errors are user facing`() =
        runBlocking {
            val fixture = fixture()
            val previousStreaming = AppConfig.instance.streamingEnabled
            try {
                AppConfig.instance.streamingEnabled = false
                coEvery { fixture.manager.sendMessage("blank") } coAnswers {
                    fixture.wiring.updateToolPreview("3 todos")
                    ""
                }
                coEvery { fixture.manager.sendMessage("error") } throws IllegalStateException("401 invalid api key")
                fixture.wiring.register()

                fixture.send.captured.invoke("blank")
                waitForUi()
                verify { fixture.chatPanel.addAssistantMessage("Ich habe das Tool ausgeführt. Ergebnis: 3 todos") }

                fixture.send.captured.invoke("error")
                waitForUi()
                verify { fixture.chatPanel.addAssistantMessage(match { it.contains("Authentication failed") }) }
            } finally {
                AppConfig.instance.streamingEnabled = previousStreaming
            }
        }

    private fun fixture(): Fixture {
        val manager = mockk<AgentManager>()
        val panel = mockk<ChatPanel>(relaxed = true)
        val send = slot<(String) -> Unit>()
        val clear = slot<() -> Unit>()
        val openTodos = slot<() -> Unit>()
        val loadOlder = slot<() -> Unit>()
        every { panel.setOnSendMessage(capture(send)) } just Runs
        every { panel.setOnClearConversation(capture(clear)) } just Runs
        every { panel.setOnOpenTodos(capture(openTodos)) } just Runs
        every { panel.setOnLoadOlderMessages(capture(loadOlder)) } just Runs
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var opened = 0
        val wiring = MainWindowChatWiring(manager, panel, scope) { opened++ }
        return Fixture(manager, panel, wiring, scope, send, clear, openTodos, loadOlder) { opened }
    }

    private suspend fun waitForUi() {
        delay(250)
        FxTestSupport.flush()
    }

    private data class Fixture(
        val manager: AgentManager,
        val chatPanel: ChatPanel,
        val wiring: MainWindowChatWiring,
        val scope: CoroutineScope,
        val send: io.mockk.CapturingSlot<(String) -> Unit>,
        val clear: io.mockk.CapturingSlot<() -> Unit>,
        val openTodos: io.mockk.CapturingSlot<() -> Unit>,
        val loadOlder: io.mockk.CapturingSlot<() -> Unit>,
        private val todoCount: () -> Int,
    ) {
        val todoOpenCount: Int
            get() = todoCount()
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun startToolkit() = FxTestSupport.startToolkit()
    }
}
