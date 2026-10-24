package de.heckenmann.visualagent.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import de.heckenmann.visualagent.protocol.MainAgentMemoryPort
import de.heckenmann.visualagent.protocol.MainAgentMemorySnapshot
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSection
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.ui.conversation.models.ConversationModelPicker
import de.heckenmann.visualagent.ui.conversation.models.rememberConversationModelState
import de.heckenmann.visualagent.ui.settings.agent.AgentSettingsPage
import de.heckenmann.visualagent.ui.settings.forms.ConversationSettingsPage
import de.heckenmann.visualagent.ui.settings.navigation.SettingsPageDraft
import de.heckenmann.visualagent.ui.settings.providers.ProviderManagementPanel
import de.heckenmann.visualagent.ui.settings.providers.ProviderManagementState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/** Retains the former overlay scenarios at their new, independently persisted destinations. */
class ComposeProviderSettingsOverlayTest {
    @get:Rule val compose = createComposeRule()
    private val settings = mockk<SettingsPort>(relaxed = true)
    private val providers = mockk<ProviderPort>(relaxed = true)
    private val memory = mockk<MainAgentMemoryPort>()
    private val ollama =
        ProviderProfile(
            "ollama",
            "Ollama",
            ProviderAdapter.OLLAMA,
            "http://localhost",
            defaultModel = "llama",
            models = listOf(ProviderModel("llama")),
        )
    private val codex =
        ProviderProfile(
            "codex",
            "Codex",
            ProviderAdapter.CODEX_CLI,
            "",
            defaultModel = "codex-2",
            models = listOf(ProviderModel("codex-1")),
        )
    private var snapshot = SettingsSnapshot(providerId = "ollama", modelId = "llama", maxParallelSubAgents = 3)

    @Before fun prepare() {
        coEvery { settings.snapshotAsync() } answers { snapshot }
        every { settings.snapshot() } answers { snapshot }
        every { providers.listProviders() } returns listOf(ollama, codex)
        every { memory.snapshot() } returns MainAgentMemorySnapshot("", 0, 12000, 0)
    }

    @Test fun `switching provider details refreshes its catalog without changing the Conversation selection`() {
        coEvery { providers.discoverModels(codex) } returns listOf(ProviderModel("codex-1"), ProviderModel("codex-2"))
        compose.setContent { MaterialTheme { ProviderManagementPanel(settings, providers) } }
        compose.onNodeWithText("Provider settings loaded").assertExists()
        compose.onNodeWithContentDescription("Configure provider: codex").performClick()
        compose.onNodeWithContentDescription("Refresh models").performScrollTo().performClick()
        compose.waitUntil { runCatching { compose.onNodeWithText("Loaded 2 models").assertExists() }.isSuccess }
        compose.onNodeWithText("codex-2").assertExists()
        coVerify(exactly = 1) { providers.discoverModels(codex) }
        verify(exactly = 0) { providers.setActiveSelection(any(), any()) }
        coVerify(exactly = 0) { settings.selectConversationModel(any(), any()) }
    }

    @Test fun `agent instructions and execution limits save without rewriting the provider catalog`() {
        var changed = false
        compose.setContent { MaterialTheme { AgentSettingsPage(settings, memory, SettingsPageDraft()) { changed = true } } }
        compose.onNodeWithContentDescription("Model instruction").performTextInput("Keep answers concise")
        compose.onNodeWithContentDescription("Increase Parallel agents").performScrollTo().performClick()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil { changed }
        verify {
            settings.saveSection(
                SettingsSection.AGENT,
                match {
                    it.userModelInstruction == "Keep answers concise" &&
                        it.maxParallelSubAgents == 4
                },
            )
        }
        verify(exactly = 0) { settings.save(any(), any()) }
        verify(exactly = 0) { providers.replaceProfiles(any(), any()) }
    }

    @Test fun `conversation setting steppers save only the composition preferences`() {
        compose.setContent { MaterialTheme { ConversationSettingsPage(settings, providers, {}, {}) } }
        compose.onNodeWithText("Conversation").performClick()
        compose.onNodeWithContentDescription("Configure prompt interpolation").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Increase Interpolation context messages").performScrollTo().performClick()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil { runCatching { compose.onNodeWithText("Saved conversation settings").assertExists() }.isSuccess }
        verify {
            settings.saveSection(
                SettingsSection.CONVERSATION,
                match {
                    it.promptInterpolationContextMessages == 9 &&
                        it.maxParallelSubAgents == 3
                },
            )
        }
    }

    @Test fun `reset button restores unsaved agent instructions`() {
        compose.setContent { MaterialTheme { AgentSettingsPage(settings, memory, SettingsPageDraft()) {} } }
        compose.onNodeWithContentDescription("Model instruction").performTextInput("Unsaved guidance")
        compose.onNodeWithText("Reset changes").performClick()
        compose.waitUntil { runCatching { compose.onNodeWithContentDescription("Model instruction").assertTextEquals("") }.isSuccess }
        compose.onNodeWithContentDescription("Model instruction").performTextInput("Reset verified")
        compose.onNodeWithContentDescription("Model instruction").assertTextEquals("Reset verified")
        verify(exactly = 0) { settings.saveSection(any(), any()) }
    }

    @Test fun `refresh and favorite actions persist through the composer without a settings save`() {
        every { settings.setModelFavorite("llama", true) } answers { snapshot = snapshot.copy(favoriteModels = listOf("llama")) }
        coEvery { providers.refreshModels("ollama") } returns ollama.models
        compose.setContent { MaterialTheme { ConversationModelPicker(rememberConversationModelState(providers, settings)) {} } }
        compose.waitUntil { runCatching { compose.onNodeWithText("llama · Ollama").assertExists() }.isSuccess }
        compose.onNodeWithContentDescription("Select conversation model").performClick()
        compose.onNodeWithContentDescription("Favorite llama").performClick()
        compose.waitUntil { runCatching { compose.onNodeWithContentDescription("Remove llama from favorites").assertExists() }.isSuccess }
        compose.onNodeWithContentDescription("Refresh current provider models").performClick()
        compose.waitUntil { runCatching { coVerify(exactly = 1) { providers.refreshModels("ollama") } }.isSuccess }
        verify { settings.setModelFavorite("llama", true) }
        verify(exactly = 0) { settings.save(any(), any()) }
    }

    @Test fun `provider remove and onboarding actions remain in provider administration`() {
        var onboarding = false
        val state = ProviderManagementState()
        compose.setContent {
            MaterialTheme {
                ProviderManagementPanel(
                    settings,
                    providers,
                    state,
                    onRunOnboarding = { onboarding = true },
                )
            }
        }
        compose.onNodeWithText("Provider settings loaded").assertExists()
        compose.onNodeWithContentDescription("Configure provider: codex").performClick()
        compose.onNodeWithContentDescription("Remove provider").performClick()
        assertTrue(state.draft.providers.none { it.id == "codex" })
        compose.onNodeWithContentDescription("Run onboarding again").performClick()
        assertTrue(onboarding)
        verify(exactly = 0) { settings.save(any(), any()) }
    }
}
