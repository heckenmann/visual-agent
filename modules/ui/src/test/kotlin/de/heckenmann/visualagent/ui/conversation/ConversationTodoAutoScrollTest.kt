package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.IdlingResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.ConversationHistoryPage
import de.heckenmann.visualagent.protocol.ConversationInputPlacement
import de.heckenmann.visualagent.protocol.ConversationMessage
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.ConversationPreferences
import de.heckenmann.visualagent.protocol.ConversationSuggestionPort
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.protocol.TodoChange
import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.protocol.TodoPort
import de.heckenmann.visualagent.protocol.TodoProgress
import de.heckenmann.visualagent.protocol.TodoState
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import de.heckenmann.visualagent.ui.status.InFlightStateHolder
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises todo-only timeline updates through the real panel and server event subscriptions. */
class ConversationTodoAutoScrollTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `created todo follows latest with pinned composer even when history is unchanged`() {
        val fixture = showPanel(ConversationInputPlacement.FIXED)
        fixture.emit(TodoItem("new", "Newest todo", timelineSequence = 41))
        compose.waitForIdle()
        assertTrue(fixture.list.conversationPosition().isAtLatest, "A todo-only event must keep the panel at its newest item")
        compose.onNodeWithText("Newest todo").assertIsDisplayed()
    }

    @Test
    fun `created todo follows latest with inline composer`() {
        val fixture = showPanel(ConversationInputPlacement.CONVERSATION_MESSAGE)
        fixture.emit(TodoItem("new", "Newest todo", timelineSequence = 41))
        compose.waitForIdle()
        assertTrue(fixture.list.conversationPosition().isAtLatest)
        compose.onNodeWithText("Newest todo").assertIsDisplayed()
    }

    @Test
    fun `created todo jumps from browsed history to the newest card`() {
        val fixture = showPanel(ConversationInputPlacement.FIXED)
        runBlocking { fixture.list.scrollToItem(12, 5) }
        compose.waitForIdle()
        fixture.emit(TodoItem("new", "Newest todo", timelineSequence = 41))
        compose.waitForIdle()
        assertTrue(fixture.list.conversationPosition().isAtLatest)
        assertEquals(
            "todo:new",
            fixture.list.layoutInfo.visibleItemsInfo
                .first()
                .key,
        )
    }

    @Test
    fun `updated and deleted todo moves to latest without a synthetic history message`() {
        val fixture = showPanel(ConversationInputPlacement.FIXED)
        val first = TodoItem("first", "First todo", timelineSequence = 41)
        fixture.emit(first)
        compose.waitForIdle()
        fixture.emit(TodoItem("second", "Second todo", timelineSequence = 42))
        compose.waitForIdle()
        fixture.emit(first.copy(status = TodoState.COMPLETED, timelineSequence = 43))
        compose.waitForIdle()
        assertTrue(fixture.list.conversationPosition().isAtLatest)
        assertEquals(
            "todo:first",
            fixture.list.layoutInfo.visibleItemsInfo
                .first()
                .key,
        )
        fixture.emit(first.copy(status = TodoState.COMPLETED, timelineSequence = 44), removed = true)
        compose.waitForIdle()
        assertTrue(fixture.list.conversationPosition().isAtLatest)
        compose.onNodeWithText("Unavailable").assertIsDisplayed()
    }

    @Test
    fun `todo progress alone returns from browsing to latest`() {
        val fixture = showPanel(ConversationInputPlacement.FIXED)
        fixture.emit(TodoItem("new", "Running todo", status = TodoState.IN_PROGRESS, timelineSequence = 41))
        compose.waitForIdle()
        runBlocking { fixture.list.scrollToItem(12, 5) }
        compose.waitForIdle()
        compose.runOnIdle {
            fixture.progress.captured(TodoProgress("new", "Incremental todo output", executionId = "execution", agentId = "worker"))
        }
        compose.waitForIdle()
        assertTrue(fixture.list.conversationPosition().isAtLatest)
        compose.onNodeWithText("Incremental todo output").assertIsDisplayed()
    }

    private fun showPanel(placement: ConversationInputPlacement): Fixture {
        val fixture = Fixture()
        val history = (1..40).map { ConversationMessage("assistant", "Message $it", id = "message-$it", timelineSequence = it.toLong()) }
        val conversation = mockk<ConversationPort>(relaxed = true)
        every { conversation.preferences() } returns ConversationPreferences(inputPlacement = placement)
        coEvery { conversation.currentHistory() } returns history
        coEvery { conversation.latest() } coAnswers {
            fixture.refreshing.set(false)
            ConversationHistoryPage(history, 0, false)
        }
        val todos = mockk<TodoPort>(relaxed = true)
        every { todos.list() } returns emptyList()
        every { todos.deletedSnapshots(any()) } returns emptyList()
        every { todos.responseSnapshots(any()) } answers {
            fixture.todosLoaded.set(true)
            emptyList()
        }
        every { todos.addListener(capture(fixture.listener)) } returns AutoCloseable {}
        every { todos.addProgressListener(capture(fixture.progress)) } returns AutoCloseable {}
        val settings = mockk<SettingsPort>(relaxed = true)
        coEvery { settings.snapshotAsync() } returns SettingsSnapshot(followUpSuggestionsEnabled = false)
        val activity = mockk<ActivityPort>(relaxed = true)
        val suggestions = mockk<ConversationSuggestionPort>(relaxed = true)
        val inFlight = InFlightStateHolder()
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(440.dp, 420.dp)) {
                    ConversationPanel(
                        modalRequester = ComposeModalRequester {},
                        inFlight = inFlight,
                        activityPort = activity,
                        todoPort = todos,
                        conversationPort = conversation,
                        suggestionPort = suggestions,
                        settingsPort = settings,
                        onScrollStateObserved = { state, list ->
                            fixture.list = list
                            if (state.history.size == history.size) fixture.historyLoaded.set(true)
                        },
                    )
                }
            }
        }
        compose.registerIdlingResource(
            object : IdlingResource {
                override val isIdleNow: Boolean
                    get() = fixture.historyLoaded.get() && fixture.todosLoaded.get() && !fixture.refreshing.get()
            },
        )
        compose.waitForIdle()
        assertTrue(fixture.list.conversationPosition().isAtLatest)
        return fixture
    }

    private class Fixture {
        lateinit var list: LazyListState
        val listener = slot<(TodoChange) -> Unit>()
        val progress = slot<(TodoProgress) -> Unit>()
        val historyLoaded = AtomicBoolean(false)
        val todosLoaded = AtomicBoolean(false)
        val refreshing = AtomicBoolean(false)

        fun emit(
            todo: TodoItem,
            removed: Boolean = false,
        ) {
            refreshing.set(true)
            listener.captured(TodoChange(todo = todo, todoId = todo.id, removed = removed))
        }
    }
}
