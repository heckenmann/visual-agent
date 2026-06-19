package de.heckenmann.visualagent.ui.panels.tests

import de.heckenmann.visualagent.ui.panels.FxTestSupport
import de.heckenmann.visualagent.ui.panels.chat.ChatConversationEventsController
import de.heckenmann.visualagent.ui.panels.chat.ChatMessage
import de.heckenmann.visualagent.ui.panels.chat.ChatMessageListController
import de.heckenmann.visualagent.ui.panels.chat.ChatMessageRenderer
import javafx.beans.property.SimpleBooleanProperty
import javafx.scene.control.ScrollPane
import javafx.scene.layout.VBox
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatMessageListControllerTest {
    @Test
    fun `message list supports append insert replace grouping and clear`() =
        FxTestSupport.run {
            val fixture = fixture()
            fixture.controller.append(ChatMessage("user", "one"))
            fixture.controller.append(ChatMessage("user", "two"))
            fixture.controller.insert(1, ChatMessage("assistant", "middle"))
            fixture.controller.replace(1, ChatMessage("assistant", "updated"))

            assertEquals(listOf("one", "updated", "two"), fixture.controller.messages.map { it.content })
            assertFalse(fixture.emptyState.isVisible)
            assertFalse(
                fixture.controller.messageRows[2]
                    .styleClass
                    .contains("chat-row-grouped"),
            )

            fixture.controller.clear()
            assertTrue(fixture.controller.messages.isEmpty())
            assertTrue(fixture.emptyState.isVisible)
        }

    @Test
    fun `set prepend and streaming update preserve list state`() {
        val fixture =
            FxTestSupport.run {
                fixture().also {
                    it.controller.setMessages(
                        listOf(
                            ChatMessage("user", "question"),
                            ChatMessage("assistant", LOADING),
                        ),
                    )
                    it.controller.loadingOlderMessages = true
                    it.controller.prepend(listOf(ChatMessage("assistant", "older")))
                    it.controller.updateStreaming("streamed")
                }
            }
        FxTestSupport.flush()

        FxTestSupport.run {
            assertEquals(listOf("older", "question", "streamed"), fixture.controller.messages.map { it.content })
            assertFalse(fixture.controller.loadingOlderMessages)
            assertEquals(-1, fixture.controller.latestLoadingIndex())
        }
    }

    @Test
    fun `conversation events replace placeholders map tools and retry user messages`() {
        val sent = mutableListOf<String>()
        val runtimeUpdates = mutableListOf<Unit>()
        FxTestSupport.run {
            val fixture = fixture()
            val waiting = SimpleBooleanProperty(false)
            val events =
                ChatConversationEventsController(
                    messageList = fixture.controller,
                    loadingToken = LOADING,
                    waitingForAssistant = waiting,
                    updateRuntimeStatus = { runtimeUpdates += Unit },
                    sendMessage = sent::add,
                    mapToolEvent = { ChatMessage("assistant", "tool:${it.toolId}", isToolEvent = true) },
                )
            fixture.controller.setMessages(
                listOf(
                    ChatMessage("user", "question"),
                    ChatMessage("assistant", LOADING),
                ),
            )

            events.addThinkingEvent(" reasoning ")
            events.addAssistantMessage("")
            assertEquals(
                "(No text response. See tool results above.)",
                fixture.controller.messages
                    .last()
                    .content,
            )
            events.retryAssistantAt(fixture.controller.messages.lastIndex)
            assertEquals(listOf("question"), sent)
            assertTrue(waiting.get())

            events.finishStreamingAssistantMessage("final")
            events.addThinkingEvent(" ")
            events.retryAssistantAt(-1)
            assertFalse(waiting.get())
            assertTrue(runtimeUpdates.isNotEmpty())
        }
        FxTestSupport.flush()
    }

    private fun fixture(): Fixture {
        val scrollPane = ScrollPane()
        val container = VBox()
        val emptyState = VBox()
        lateinit var controller: ChatMessageListController
        val renderer =
            ChatMessageRenderer(
                loadingToken = LOADING,
                timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()),
                previousRole = { index -> controller.messages.getOrNull(index - 1)?.role },
                retryAtRow = {},
            )
        controller = ChatMessageListController(scrollPane, container, emptyState, renderer, LOADING)
        return Fixture(controller, emptyState)
    }

    private data class Fixture(
        val controller: ChatMessageListController,
        val emptyState: VBox,
    )

    companion object {
        private const val LOADING = "__loading__"

        @JvmStatic
        @BeforeAll
        fun startToolkit() = FxTestSupport.startToolkit()
    }
}
