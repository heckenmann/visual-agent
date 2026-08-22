@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.workspace

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
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

    @Test
    fun `workspace panel visibility removes content after closing transition`() {
        var visible by mutableStateOf(true)
        composeTestRule.setContent {
            MaterialTheme {
                WorkspacePanelVisibility(visible = visible) {
                    Text("animated workspace panel")
                }
            }
        }

        composeTestRule.onNodeWithText("animated workspace panel").assertExists()
        visible = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("animated workspace panel").assertDoesNotExist()
    }
}
