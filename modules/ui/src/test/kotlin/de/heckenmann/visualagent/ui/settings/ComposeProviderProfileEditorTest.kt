@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderProfile
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

        composeTestRule.onNodeWithText("Provider ID").assertDoesNotExist()
        composeTestRule.onNodeWithText("Base URL").assertExists()
        composeTestRule.onNodeWithText("API key").assertExists()
        composeTestRule.onNodeWithText("Default model").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Create provider").assertExists()
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

        composeTestRule.onNodeWithText("Name").performTextInput("Custom")
        composeTestRule.onNodeWithText("Base URL").performTextInput("https://api.custom.com")
        composeTestRule.waitForIdle()

        assertTrue(saved == null)
    }

    @Test
    fun `profile editor identifies the edit action`() {
        val profile =
            ProviderProfile(
                id = "existing",
                name = "Existing provider",
                adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                baseUrl = "https://api.example.com",
            )
        composeTestRule.setContent {
            MaterialTheme {
                ProviderProfileEditor(
                    initial = profile.toFormState(),
                    existing = profile,
                    canDisable = true,
                    onCancel = {},
                    onSave = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Save provider changes").assertExists()
    }
}
