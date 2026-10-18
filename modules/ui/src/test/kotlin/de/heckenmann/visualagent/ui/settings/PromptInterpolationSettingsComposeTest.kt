package de.heckenmann.visualagent.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies staged auxiliary settings do not change the main-agent selection. */
class PromptInterpolationSettingsComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `interpolation selection excludes non chat models and providers`() {
        val embedding = ProviderModel("embedding", capabilities = setOf("embedding"), capabilitiesComplete = true)
        val chat = ProviderModel("chat", capabilities = setOf("chat"), capabilitiesComplete = true)
        val mixed =
            ProviderProfile(
                "mixed",
                "Mixed",
                ProviderAdapter.OPENAI_COMPATIBLE,
                "https://example.test",
                defaultModel = "embedding",
                models = listOf(embedding, chat),
            )
        val onlyEmbedding = mixed.copy(id = "only-embedding", name = "Embeddings only", models = listOf(embedding))
        val state = mutableStateOf(SettingsSnapshot())

        compose.setContent {
            MaterialTheme { promptInterpolationSettings(state.value, listOf(mixed, onlyEmbedding)) { state.value = it } }
        }
        compose.onNodeWithText("Use active provider and model").performClick()
        compose.onNodeWithText("Embeddings only").assertDoesNotExist()
        compose.onNodeWithText("Mixed").performClick()
        assertEquals("mixed", state.value.promptInterpolationProviderId)
        assertEquals("chat", state.value.promptInterpolationModelId)
        compose.onNodeWithText("chat").assertExists()
        compose.onNodeWithText("embedding").assertDoesNotExist()
    }

    @Test
    fun `automatic toggle and dedicated model are independent of main selection`() {
        val state = mutableStateOf(SettingsSnapshot(providerId = "main", modelId = "main-model"))
        val provider =
            ProviderProfile(
                "other",
                "Other provider",
                ProviderAdapter.OLLAMA,
                "http://localhost",
                defaultModel = "rewrite",
                models = listOf(ProviderModel("rewrite")),
            )
        compose.setContent {
            MaterialTheme { promptInterpolationSettings(state.value, listOf(provider)) { state.value = it } }
        }
        assertFalse(state.value.automaticPromptInterpolation)
        compose.onNodeWithContentDescription("Automatic prompt interpolation").performClick()
        assertTrue(state.value.automaticPromptInterpolation)
        compose.onNodeWithText("Use active provider and model").performClick()
        compose.onNodeWithText("Other provider").performClick()
        assertEquals("other", state.value.promptInterpolationProviderId)
        assertEquals("rewrite", state.value.promptInterpolationModelId)
        assertEquals("main", state.value.providerId)
        assertEquals("main-model", state.value.modelId)
    }
}
