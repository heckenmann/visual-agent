package de.heckenmann.visualagent.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderProfile
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies provider profile editing without application or Spring dependencies. */
class ComposeProviderProfileEditorTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `existing network provider renders and saves profile fields`() {
        val profile =
            ProviderProfile(
                id = "ollama",
                name = "Ollama",
                adapter = ProviderAdapter.OLLAMA,
                baseUrl = "http://localhost:11434",
                apiKey = "secret",
                defaultModel = "llama3",
                models = listOf(ProviderModel("llama3")),
                options = mapOf("keep_alive" to "5m"),
            )
        var saved: ProviderProfile? = null

        composeTestRule.setContent {
            MaterialTheme {
                ProviderProfileEditor(profile.toFormState(), profile, canDisable = true, onCancel = {}, onSave = { saved = it })
            }
        }

        composeTestRule.onNodeWithText("Base URL").assertExists()
        composeTestRule.onNodeWithText("API key").assertExists()
        composeTestRule.onNodeWithText("Provider options (key=value per line)").assertExists()
        composeTestRule.onNodeWithContentDescription("Save provider changes").performScrollTo().performClick()

        assertEquals(profile, saved)
    }

    @Test
    fun `codex profile omits network credentials and form helpers preserve options`() {
        val state =
            ProviderProfileFormState(
                id = "codex",
                name = "Codex",
                adapter = ProviderAdapter.CODEX_CLI,
                defaultModel = "gpt-5",
            )

        composeTestRule.setContent {
            MaterialTheme {
                ProviderProfileEditor(state, existing = null, canDisable = true, onCancel = {}, onSave = {})
            }
        }

        composeTestRule.onAllNodesWithText("Base URL").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("API key").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Provider options (key=value per line)").assertCountEquals(0)
        assertEquals(mapOf("a" to "1", "b" to "2"), "a=1\nb=2\ninvalid".toSettingsMap())
        assertEquals("a=1\nb=2", mapOf("b" to "2", "a" to "1").toSettingsMapText())
        assertTrue(newProviderFormState().id.startsWith("provider-"))
    }
}
