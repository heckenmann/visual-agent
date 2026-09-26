@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ConversationMessage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Verifies a message timestamp does not own a separate window during lazy-list scrolling. */
class ConversationTimestampScrollTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `hover timestamp stays in the message composition while lazy rows are recycled`() {
        val timestamp = 1_700_000_000_000L
        val formattedTimestamp =
            DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Locale.getDefault())
                .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
        val states = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()
        composeTestRule.setContent {
            val state = rememberLazyListState()
            states += state
            MaterialTheme {
                LazyColumn(state = state, modifier = Modifier.width(360.dp).height(120.dp)) {
                    items(30) { index ->
                        conversationMessageActionMenu(
                            message = ConversationMessage("user", "Message $index", id = "message-$index"),
                            canEdit = false,
                            canDelete = false,
                            canRetry = false,
                            onEdit = {},
                            onDelete = {},
                            onRetry = {},
                            timestamp = timestamp,
                            showTimestamp = index == 0,
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithText(formattedTimestamp).assertExists()
        composeTestRule.onAllNodes(isPopup()).assertCountEquals(0)
        runBlocking { states.last().scrollToItem(20) }
        composeTestRule.waitForIdle()
        runBlocking { states.last().scrollToItem(0) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(formattedTimestamp).assertExists()
        composeTestRule.onAllNodes(isPopup()).assertCountEquals(0)
    }
}
