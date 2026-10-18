package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.ConversationHistoryPage
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
import de.heckenmann.visualagent.ui.components.LocalMarkdownImmediateParsing
import de.heckenmann.visualagent.ui.modal.ComposeContentModal
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import de.heckenmann.visualagent.ui.status.InFlightStateHolder
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Exercises opening an empty todo through the real Conversation panel and subsequent server events. */
class ConversationTodoDetailsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `details opened before output follow progress and current status`() {
        val todo = TodoItem("one", "First task", timelineSequence = 1)
        val changes = slot<(TodoChange) -> Unit>()
        val progress = slot<(TodoProgress) -> Unit>()
        val todos = mockk<TodoPort>(relaxed = true)
        every { todos.list() } returns listOf(todo)
        every { todos.deletedSnapshots(any()) } returns emptyList()
        every { todos.responseSnapshots(any()) } returns emptyList()
        every { todos.addListener(capture(changes)) } returns AutoCloseable {}
        every { todos.addProgressListener(capture(progress)) } returns AutoCloseable {}
        val conversation = mockk<ConversationPort>(relaxed = true)
        every { conversation.preferences() } returns ConversationPreferences()
        coEvery { conversation.currentHistory() } returns emptyList()
        coEvery { conversation.latest() } returns ConversationHistoryPage(emptyList(), 0, false)
        val settings = mockk<SettingsPort>(relaxed = true)
        coEvery { settings.snapshotAsync() } returns SettingsSnapshot(followUpSuggestionsEnabled = false)
        var modal by mutableStateOf<ComposeContentModal?>(null)
        val inFlight = InFlightStateHolder()
        val activity = mockk<ActivityPort>(relaxed = true)
        val suggestions = mockk<ConversationSuggestionPort>(relaxed = true)
        compose.setContent {
            CompositionLocalProvider(LocalMarkdownImmediateParsing provides true) {
                MaterialTheme {
                    Box(Modifier.size(480.dp, 500.dp)) {
                        ConversationPanel(
                            modalRequester = ComposeModalRequester { modal = it as ComposeContentModal },
                            inFlight = inFlight,
                            activityPort = activity,
                            todoPort = todos,
                            conversationPort = conversation,
                            suggestionPort = suggestions,
                            settingsPort = settings,
                        )
                        modal?.content { modal = null }
                    }
                }
            }
        }
        compose.waitUntil {
            compose
                .onAllNodes(
                    androidx.compose.ui.test
                        .hasContentDescription("Todo: First task"),
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithContentDescription("Todo: First task").performClick()
        compose.onNodeWithText("No response output yet.").assertExists()
        compose.runOnIdle {
            changes.captured(TodoChange(todo = todo.copy(status = TodoState.IN_PROGRESS), todoId = todo.id))
            progress.captured(
                TodoProgress(todoId = todo.id, executionId = "run", agentId = "worker", delta = "Live output", completed = false),
            )
        }
        compose.waitForIdle()
        compose.onAllNodesWithText("Live output", useUnmergedTree = true).assertCountEquals(2)
        compose.runOnIdle {
            changes.captured(TodoChange(todo = todo.copy(status = TodoState.COMPLETED), todoId = todo.id))
            progress.captured(TodoProgress(todoId = todo.id, executionId = "run", agentId = "worker", delta = "", completed = true))
        }
        compose.waitForIdle()
        compose.onNodeWithText("Completed · worker").assertExists()
        assertEquals("Todo response", modal?.title)
    }
}
