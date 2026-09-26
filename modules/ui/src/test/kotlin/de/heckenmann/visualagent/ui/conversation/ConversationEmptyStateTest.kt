@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertTrue

/** Verifies the first-message prompt uses the available viewport instead of a message-card layout. */
class ConversationEmptyStateTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `empty state is centered and invites the user to use the composer`() {
        composeTestRule.setContent {
            MaterialTheme {
                Box(Modifier.size(360.dp, 260.dp)) {
                    ConversationEmptyState()
                }
            }
        }

        val title = composeTestRule.onNodeWithText("No conversation yet").getBoundsInRoot()
        val body = composeTestRule.onNodeWithText("Write a message below to begin.").getBoundsInRoot()
        val titleCenterY = (title.top + title.bottom) / 2f
        val bodyCenterY = (body.top + body.bottom) / 2f
        assertTrue(abs((titleCenterY - 130.dp).value) < 48f)
        assertTrue(bodyCenterY > titleCenterY)
    }
}
