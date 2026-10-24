package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.ui.conversation.models.ConversationModelPicker
import de.heckenmann.visualagent.ui.conversation.models.rememberConversationModelState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test

/** The previous model-context scenario now runs through immediate composer selection. */
class ComposeProviderSettingsContextTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `selecting a model adopts its reported context limit in either direction`() {
        val settings = mockk<SettingsPort>(relaxed = true)
        val providers = mockk<ProviderPort>(relaxed = true)
        val profile =
            ProviderProfile(
                "ollama",
                "Ollama",
                ProviderAdapter.OLLAMA,
                "http://localhost",
                defaultModel = "small-model",
                models =
                    listOf(
                        ProviderModel("small-model", contextLimit = 8192),
                        ProviderModel("large-model", contextLimit = 65536),
                        ProviderModel("medium-model", contextLimit = 16384),
                    ),
            )
        var current = SettingsSnapshot(providerId = "ollama", modelId = "small-model", contextLength = 4096)
        every { settings.snapshot() } answers { current }
        every { providers.listProviders() } returns listOf(profile)
        coEvery { settings.selectConversationModel("ollama", any()) } coAnswers {
            val id = secondArg<String>()
            current = current.copy(modelId = id, contextLength = profile.models.first { it.id == id }.contextLimit!!)
        }
        compose.setContent {
            MaterialTheme {
                val state = rememberConversationModelState(providers, settings)
                Column {
                    ConversationModelPicker(state) {}
                    Text("Budget: ${state.selected.contextLength}")
                }
            }
        }
        compose.waitUntil { runCatching { compose.onNodeWithText("small-model · Ollama").assertExists() }.isSuccess }
        compose.onNodeWithText("Budget: 4096").assertExists()
        select("large-model", 65536)
        select("medium-model", 16384)
        coVerify { settings.selectConversationModel("ollama", "large-model") }
        coVerify { settings.selectConversationModel("ollama", "medium-model") }
        verify(exactly = 0) { settings.save(any(), any()) }
    }

    private fun select(
        id: String,
        limit: Int,
    ) {
        compose.onNodeWithContentDescription("Select conversation model").performClick()
        compose.onNodeWithContentDescription("Use Ollama: $id").performClick()
        compose.waitUntil { runCatching { compose.onNodeWithText("Budget: $limit").assertExists() }.isSuccess }
    }
}
