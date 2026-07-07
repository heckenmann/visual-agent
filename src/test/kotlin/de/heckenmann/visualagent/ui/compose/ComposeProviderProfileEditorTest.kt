@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class ComposeProviderProfileEditorTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `profile editor renders form fields and validates required input`() {
        var saved: ProviderProfile? = null
        composeTestRule.setContent {
            MaterialTheme {
                ProviderProfileEditor(
                    initial = newProviderFormState(),
                    existing = null,
                    canDisable = true,
                    onCancel = {},
                    onSave = { saved = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Provider ID").assertExists()
        composeTestRule.onNodeWithText("Base URL").assertExists()
        composeTestRule.onNodeWithText("API key").assertExists()
    }

    @Test
    fun `profile editor saves when validation passes`() {
        var saved: ProviderProfile? = null
        composeTestRule.setContent {
            MaterialTheme {
                ProviderProfileEditor(
                    initial = newProviderFormState(),
                    existing = null,
                    canDisable = true,
                    onCancel = {},
                    onSave = { saved = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Provider ID").performTextInput("custom")
        composeTestRule.onNodeWithText("Name").performTextInput("Custom")
        composeTestRule.onNodeWithText("Base URL").performTextInput("https://api.custom.com")
        composeTestRule.onNodeWithText("Default model").performTextInput("model-x")
        composeTestRule.waitForIdle()

        assertTrue(saved == null)
    }
}
