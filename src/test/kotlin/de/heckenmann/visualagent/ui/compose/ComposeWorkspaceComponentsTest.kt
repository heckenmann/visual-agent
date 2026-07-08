@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import org.junit.Assert.assertTrue
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

    @Test
    fun `panel resizer reports a new width when dragged right`() {
        var resizedWidth = 0
        composeTestRule.setContent {
            MaterialTheme {
                PanelResizer(
                    currentWidth = 300,
                    onWidthChanged = { resizedWidth = it },
                    minPanelWidth = 200,
                )
            }
        }

        composeTestRule.waitForIdle()
        // Drag far enough to cross the resizer threshold (10 px) at least once.
        composeTestRule
            .onNodeWithContentDescription("Resize panel")
            .performTouchInput {
                swipeRight(
                    startX = centerX,
                    endX = centerX + 100f,
                )
            }
        composeTestRule.waitForIdle()

        assertTrue("Expected resized width > 300 but was $resizedWidth", resizedWidth > 300)
    }
}
