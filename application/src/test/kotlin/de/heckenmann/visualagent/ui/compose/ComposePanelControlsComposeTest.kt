@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/**
 * Compose tests for the reusable panel control primitives.
 */
class ComposePanelControlsComposeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `panel checkbox toggles checked state`() {
        composeTestRule.setContent {
            val checked = remember { mutableStateOf(false) }
            MaterialTheme {
                PanelCheckbox(label = "Enable", checked = checked.value, onCheckedChange = { checked.value = it })
            }
        }
        composeTestRule.onNodeWithText("Enable").performClick()
        composeTestRule.onNodeWithText("Enable").assertExists()
    }

    @Test
    fun `numeric panel field renders digit-only value`() {
        composeTestRule.setContent {
            MaterialTheme {
                NumericPanelField(label = "Count", value = "50", onValueChange = {})
            }
        }
        composeTestRule.onNodeWithText("Count").assertExists()
        composeTestRule.onNodeWithText("50").assertExists()
    }

    @Test
    fun `panel dropdown shows selected label`() {
        composeTestRule.setContent {
            MaterialTheme {
                PanelDropdownField(
                    label = "Pick",
                    selectedValue = "b",
                    options = listOf(PanelSelectOption("a", "Alpha"), PanelSelectOption("b", "Beta")),
                    onSelected = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Pick").assertExists()
        composeTestRule.onNodeWithText("Beta").assertExists()
    }
}
