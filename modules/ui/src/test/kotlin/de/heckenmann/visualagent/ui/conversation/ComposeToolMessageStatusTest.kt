@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ConversationMessage
import org.junit.Rule
import org.junit.Test

class ComposeToolMessageStatusTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `timeout and cancellation status updates remain visible in the same tool row`() {
        var metadata by mutableStateOf("""{"toolId":"terminal","status":"timeout"}""")
        composeTestRule.setContent {
            MaterialTheme {
                Box(Modifier.size(900.dp, 400.dp)) {
                    ToolMessageRow(
                        message = ConversationMessage("tool", "", id = "call-1", metadata = metadata),
                        isDeleting = false,
                        isInFlight = false,
                        onDelete = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Timed out").assertExists()
        composeTestRule.runOnIdle { metadata = """{"toolId":"terminal","status":"cancelled"}""" }
        composeTestRule.onNodeWithText("Cancelled").assertExists()
        composeTestRule.onNodeWithText("Timed out").assertDoesNotExist()
    }
}
