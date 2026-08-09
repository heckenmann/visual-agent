@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
