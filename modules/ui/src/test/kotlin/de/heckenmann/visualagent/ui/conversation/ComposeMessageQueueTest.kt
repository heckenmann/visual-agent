package de.heckenmann.visualagent.ui.conversation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Verifies queue-strip actions delegate to the current queued message and queue state. */
class ComposeMessageQueueTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `send now clear and flush controls update queued message state`() {
        val queue = MessageQueue()
        val messageId = queue.enqueue("Queued request", QueuedMessageSource.USER)
        var sentId: String? = null
        composeTestRule.setContent {
            MaterialTheme {
                MessageQueueStrip(
                    queue = queue,
                    onSendNow = { sentId = it.id },
                    onClear = { queue.clear() },
                    onToggleFlushMode = {
                        queue.flushMode =
                            if (queue.flushMode == QueueFlushMode.ONE_BY_ONE) {
                                QueueFlushMode.ALL_AT_ONCE
                            } else {
                                QueueFlushMode.ONE_BY_ONE
                            }
                    },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Send now").performClick()
        assertEquals(messageId, sentId)
        composeTestRule.onNodeWithText("Flush: one-by-one").performClick()
        assertEquals(QueueFlushMode.ALL_AT_ONCE, queue.flushMode)
        composeTestRule.onNodeWithContentDescription("Clear queue").performClick()
        assertEquals(0, queue.size)
    }
}
