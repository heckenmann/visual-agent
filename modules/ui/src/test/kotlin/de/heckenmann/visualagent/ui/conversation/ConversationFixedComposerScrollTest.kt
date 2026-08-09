@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies scroll-to-latest geometry when the conversation composer overlays the list. */
class ConversationFixedComposerScrollTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `newest message is fully visible above fixed composer after scroll down`() {
        val fullHistory =
            (1..100).map { index ->
                Message("user", if (index == 100) "newest" else "message $index", id = "message-$index")
            }
        val latestPage = fullHistory.takeLast(20)
        val history = mutableStateOf(fullHistory)
        val states = mutableListOf<androidx.compose.foundation.lazy.LazyListState>()

        composeTestRule.setContent {
            val listState = rememberLazyListState()
            states.add(listState)
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = true,
                        contentPadding = PaddingValues(bottom = 160.dp),
                    ) {
                        items(history.value.reversed(), key = { it.id!! }) { message ->
                            Text(message.content, modifier = Modifier.fillMaxWidth().height(48.dp))
                        }
                    }
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(160.dp)
                                .semantics { contentDescription = "Fixed composer" },
                    )
                    ConversationScrollToBottomArea(
                        isAtBottom = false,
                        listState = listState,
                        scope = rememberCoroutineScope(),
                        onHistoryRefresh = { history.value = latestPage },
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        kotlinx.coroutines.runBlocking { states.single().scrollToItem(80) }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Scroll to latest message").performClick()
        composeTestRule.waitForIdle()

        val newestBounds = composeTestRule.onNodeWithText("newest").fetchSemanticsNode().boundsInRoot
        val composerBounds = composeTestRule.onNodeWithContentDescription("Fixed composer").fetchSemanticsNode().boundsInRoot
        assertFalse(states.single().canScrollBackward, "conversation must be at the absolute newest end")
        assertTrue(
            newestBounds.bottom <= composerBounds.top,
            "newest message $newestBounds must be fully above fixed composer $composerBounds",
        )
    }
}
