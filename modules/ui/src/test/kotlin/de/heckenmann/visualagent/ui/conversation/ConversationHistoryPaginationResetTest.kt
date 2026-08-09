@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.Message
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
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

/**
 * Reproduces resetting pagination after returning to the latest conversation page.
 */
class ConversationHistoryPaginationResetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `loads older history again after returning to the latest page`() {
        val latestPage = (1..3).map { Message("user", "latest $it", id = "latest-$it") }
        val olderPage = listOf(Message("user", "older", id = "older-1"))
        var history by mutableStateOf(latestPage)
        var isAtEnd by mutableStateOf(true)
        var paginationResetVersion by mutableStateOf(0)
        val loadCalls = AtomicInteger()
        val mockManager = mockk<AgentManager>(relaxed = true)
        every { mockManager.loadOlderHistory(any()) } answers {
            if (loadCalls.incrementAndGet() == 1) emptyList() else olderPage
        }
        every { mockManager.getHistory() } returns olderPage + latestPage

        composeTestRule.setContent {
            val listState = rememberLazyListState()
            MaterialTheme {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.width(200.dp).height(160.dp),
                    reverseLayout = true,
                ) {
                    items(history.size) { index -> Text(history[index].content, modifier = Modifier.height(40.dp)) }
                }
                ConversationOlderHistoryLoader(
                    isAtEnd = isAtEnd,
                    history = history,
                    listState = listState,
                    agentManager = mockManager,
                    onHistoryChange = { history = it },
                    paginationResetVersion = paginationResetVersion,
                )
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(1, loadCalls.get(), "the first exhausted page load should run once")

        composeTestRule.runOnIdle {
            paginationResetVersion++
            isAtEnd = false
        }
        composeTestRule.waitForIdle()
        assertEquals(1, loadCalls.get(), "scroll-down must not immediately reload older history")

        composeTestRule.runOnIdle { isAtEnd = true }
        composeTestRule.waitUntil(timeoutMillis = 5_000) { loadCalls.get() == 2 }

        assertEquals(2, loadCalls.get(), "scrolling back to the old end should load older history again")
    }
}
