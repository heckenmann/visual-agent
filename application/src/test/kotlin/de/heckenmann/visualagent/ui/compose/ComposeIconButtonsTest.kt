@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the small icon-only action button and its tooltip wrapper.
 */
class ComposeIconButtonsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `action icon button invokes onClick`() {
        var clicked = false
        composeTestRule.setContent {
            MaterialTheme {
                ActionIconButton(
                    icon = Icons.Filled.Add,
                    description = "Add item",
                    onClick = { clicked = true },
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Add item").performClick()
        assertTrue(clicked)
    }

    @Test
    fun `disabled action icon button does not invoke onClick`() {
        var clicked = false
        composeTestRule.setContent {
            MaterialTheme {
                ActionIconButton(
                    icon = Icons.Filled.Add,
                    description = "Add item",
                    enabled = false,
                    onClick = { clicked = true },
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Add item").performClick()
        assertFalse(clicked)
    }

    @Test
    fun `action icon button long click invokes onLongClick`() {
        var longClicked = false
        composeTestRule.setContent {
            MaterialTheme {
                ActionIconButton(
                    icon = Icons.Filled.Add,
                    description = "Add item",
                    onClick = {},
                    onLongClick = { longClicked = true },
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Add item").performTouchInput { longClick() }
        assertTrue(longClicked)
    }
}
