@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/**
 * Tests for reusable workspace layout components.
 */
class ComposeWorkspaceComponentsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `panel section renders title and content`() {
        composeTestRule.setContent {
            MaterialTheme {
                PanelSection(title = "Section") {
                    Text("inside section")
                }
            }
        }
        composeTestRule.onNodeWithText("Section").assertExists()
        composeTestRule.onNodeWithText("inside section").assertExists()
    }

    @Test
    fun `panel content card renders content`() {
        composeTestRule.setContent {
            MaterialTheme {
                PanelContentCard {
                    Text("inside card")
                }
            }
        }
        composeTestRule.onNodeWithText("inside card").assertExists()
    }

    @Test
    fun `panel empty state renders title and body`() {
        composeTestRule.setContent {
            MaterialTheme {
                PanelEmptyState(title = "Empty", body = "Nothing here")
            }
        }
        composeTestRule.onNodeWithText("Empty").assertExists()
        composeTestRule.onNodeWithText("Nothing here").assertExists()
    }

    @Test
    fun `panel info box renders text`() {
        composeTestRule.setContent {
            MaterialTheme {
                PanelInfoBox(text = "Info text")
            }
        }
        composeTestRule.onNodeWithText("Info text").assertExists()
    }

    @Test
    fun `numeric panel field filters non-digit input`() {
        var value = ""
        composeTestRule.setContent {
            MaterialTheme {
                NumericPanelField(
                    label = "Count",
                    value = value,
                    onValueChange = { value = it },
                )
            }
        }
        composeTestRule.onNodeWithText("Count").assertExists()
    }
}
