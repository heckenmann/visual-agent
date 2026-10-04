package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.IdlingResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.ConversationCompletionEvent
import de.heckenmann.visualagent.protocol.ConversationHistoryPage
import de.heckenmann.visualagent.protocol.ConversationMessage
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.ConversationSuggestionPort
import de.heckenmann.visualagent.protocol.TodoChange
import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.protocol.TodoPort
import de.heckenmann.visualagent.protocol.TodoState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

/** Verifies post-persistence history refreshes without timing assumptions or synthetic tool calls. */
class ConversationActivityHistoryEffectTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `persisted completion refreshes feedback after an earlier terminal refresh without blocking UI`() {
        val activity = mockk<ActivityPort>()
        val conversation = mockk<ConversationPort>()
        val suggestions = mockk<ConversationSuggestionPort>()
        val todos = mockk<TodoPort>()
        val todoListener = slot<(TodoChange) -> Unit>()
        val completionListener = slot<(ConversationCompletionEvent) -> Unit>()
        every { activity.addToolListener(any()) } returns AutoCloseable {}
        every { activity.addDownloadListener(any()) } returns AutoCloseable {}
        every { suggestions.addCompletionListener(capture(completionListener)) } returns AutoCloseable {}
        every { todos.list() } returns emptyList()
        every { todos.deletedSnapshots(any()) } returns emptyList()
        every { todos.responseSnapshots(any()) } returns emptyList()
        every { todos.addListener(capture(todoListener)) } returns AutoCloseable {}
        every { todos.addProgressListener(any()) } returns AutoCloseable {}
        val requests = Channel<Thread>(Channel.UNLIMITED)
        val pages = Channel<ConversationHistoryPage>(Channel.UNLIMITED)
        val rendered = Channel<List<ConversationMessage>>(Channel.UNLIMITED)
        val applyingHistory = AtomicBoolean(false)
        composeTestRule.registerIdlingResource(
            object : IdlingResource {
                override val isIdleNow: Boolean
                    get() = !applyingHistory.get()
            },
        )
        coEvery { conversation.latest() } coAnswers {
            requests.send(Thread.currentThread())
            pages.receive()
        }
        val older = ConversationMessage("user", "Older loaded message", id = "older")
        val recent = ConversationMessage("user", "Requested work", id = "recent")
        val terminal = ConversationMessage("system", "Todo completed", id = "terminal")
        val feedback = ConversationMessage("assistant", "The reviewed output is ready.", id = "approval")
        val state = ConversationUiState(listOf(older, recent))
        state.streamingEntryId = "another-turn"
        state.streaming.value = "Concurrent stream"
        composeTestRule.setContent {
            ConversationActivityHistoryEffect(activity, conversation, state, suggestions)
            rememberConversationTodoState(todos, conversation, state)
            LaunchedEffect(state.history) {
                rendered.send(state.history)
                applyingHistory.set(false)
            }
            MaterialTheme {
                Column { state.history.forEach { Text(it.content) } }
            }
        }
        runBlocking { rendered.receive() }
        todoListener.captured(TodoChange(todo = TodoItem("todo", "Requested work", TodoState.COMPLETED)))
        composeTestRule.waitForIdle()
        val firstReader = runBlocking { requests.receive() }
        composeTestRule.runOnIdle { assertNotSame(Thread.currentThread(), firstReader) }
        applyingHistory.set(true)
        runBlocking { pages.send(ConversationHistoryPage(listOf(recent, terminal), 0, false)) }
        composeTestRule.waitForIdle()
        assertEquals(listOf(older, recent, terminal), runBlocking { rendered.receive() })

        composeTestRule.runOnIdle { state.sending = true }
        completionListener.captured(ConversationCompletionEvent(feedback.id!!, 4))
        composeTestRule.waitForIdle()
        val completionReader = runBlocking { requests.receive() }
        composeTestRule.runOnIdle {
            assertNotSame(Thread.currentThread(), completionReader)
            state.input = "The composer remains usable while history I/O is blocked"
            assertEquals("Concurrent stream", state.streaming.value)
        }
        applyingHistory.set(true)
        runBlocking { pages.send(ConversationHistoryPage(listOf(recent, terminal, feedback), 0, false)) }
        composeTestRule.waitForIdle()
        assertEquals(listOf(older, recent, terminal, feedback), runBlocking { rendered.receive() })
        composeTestRule.onNodeWithText(feedback.content).assertExists()
        composeTestRule.runOnIdle {
            assertEquals(true, state.sending)
            assertEquals("another-turn", state.streamingEntryId)
            assertEquals("Concurrent stream", state.streaming.value)
        }
    }

    @Test
    fun `late todo page cannot overwrite the persisted completion page`() {
        val state = ConversationUiState(emptyList())
        val todoRefresh = state.beginLatestRequest()
        val completionRefresh = state.beginLatestRequest()
        val feedback = ConversationMessage("assistant", "Approved output", id = "approval")
        assertEquals(true, state.applyLatest(completionRefresh, ConversationHistoryPage(listOf(feedback), 0, false)))
        assertEquals(false, state.applyLatest(todoRefresh, ConversationHistoryPage(emptyList(), 0, false)))
        assertEquals(listOf(feedback), state.history)
    }

    @Test
    fun `port replacement and panel disposal close all refresh subscriptions`() {
        val activity = mockk<ActivityPort>()
        val conversation = mockk<ConversationPort>()
        val first = mockk<ConversationSuggestionPort>()
        val second = mockk<ConversationSuggestionPort>()
        val firstCompletionHandle = mockk<AutoCloseable>(relaxed = true)
        val secondCompletionHandle = mockk<AutoCloseable>(relaxed = true)
        val toolHandle = mockk<AutoCloseable>(relaxed = true)
        val downloadHandle = mockk<AutoCloseable>(relaxed = true)
        every { activity.addToolListener(any()) } returns toolHandle
        every { activity.addDownloadListener(any()) } returns downloadHandle
        every { first.addCompletionListener(any()) } returns firstCompletionHandle
        every { second.addCompletionListener(any()) } returns secondCompletionHandle
        val state = ConversationUiState(emptyList())
        var selectedPort by mutableStateOf(first)
        var visible by mutableStateOf(true)
        composeTestRule.setContent {
            if (visible) ConversationActivityHistoryEffect(activity, conversation, state, selectedPort)
        }
        composeTestRule.runOnIdle { selectedPort = second }
        composeTestRule.waitForIdle()
        verify(exactly = 1) { firstCompletionHandle.close() }
        verify(exactly = 0) { secondCompletionHandle.close() }
        composeTestRule.runOnIdle { visible = false }
        composeTestRule.waitForIdle()
        verify(exactly = 1) { secondCompletionHandle.close() }
        verify(exactly = 2) { toolHandle.close() }
        verify(exactly = 2) { downloadHandle.close() }
        verify(exactly = 1) { first.addCompletionListener(any()) }
        verify(exactly = 1) { second.addCompletionListener(any()) }
    }
}
