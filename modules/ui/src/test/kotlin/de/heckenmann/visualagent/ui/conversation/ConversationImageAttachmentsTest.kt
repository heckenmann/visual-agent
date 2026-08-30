package de.heckenmann.visualagent.ui.conversation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test

/** Tests for inline conversation image attachments. */
class ConversationImageAttachmentsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `renders a validated canvas image attachment`() {
        val canvasImage =
            "data:image/png;base64," +
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="

        composeTestRule.setContent {
            MaterialTheme {
                ConversationImageAttachments(listOf(canvasImage))
            }
        }

        composeTestRule.waitForIdle()
        Thread.sleep(100)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Embedded image 1").assertExists()
    }
}
