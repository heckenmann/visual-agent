package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import de.heckenmann.visualagent.agent.Message
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class ConversationStartupScrollTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `scrolls to bottom on startup when conversation history is not empty`() {
        val messages = (1..20).map { Message("user", "message $it") }
        val listState = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()
        composeTestRule.setContent {
            val state = rememberLazyListState()
            listState.add(state)
            LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                items(messages) { message ->
                    Text(message.content)
                }
            }
            ConversationStartupScrollEffect(messages, state)
        }
        composeTestRule.waitForIdle()
        val info = listState.single().layoutInfo
        val lastVisibleIndex = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        assertTrue(
            lastVisibleIndex >= messages.lastIndex,
            "expected last message to be visible after startup scroll, but last visible index was $lastVisibleIndex of ${messages.size}",
        )
    }
}
