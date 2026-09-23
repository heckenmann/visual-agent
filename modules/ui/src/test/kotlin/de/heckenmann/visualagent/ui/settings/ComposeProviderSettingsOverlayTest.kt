package de.heckenmann.visualagent.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import de.heckenmann.visualagent.protocol.MainAgentMemoryPort
import de.heckenmann.visualagent.protocol.MainAgentMemorySnapshot
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/** Verifies that provider and model edits stay in the global overlay until explicitly saved. */
class ComposeProviderSettingsOverlayTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `switching providers refreshes models and restores persisted selections`() {
        val settings = mockk<SettingsPort>(relaxed = true)
        val providers = mockk<ProviderPort>(relaxed = true)
        val memory = mockk<MainAgentMemoryPort>()
        val ollama =
            ProviderProfile(
                id = "ollama",
                name = "Ollama",
                adapter = ProviderAdapter.OLLAMA,
                baseUrl = "http://localhost:11434",
                defaultModel = "llama-2",
                models = listOf(ProviderModel("llama-1"), ProviderModel("llama-2")),
            )
        val codex =
            ProviderProfile(
                id = "codex",
                name = "Codex",
                adapter = ProviderAdapter.CODEX_CLI,
                baseUrl = "",
                defaultModel = "codex-2",
                models = listOf(ProviderModel("codex-1")),
            )
        coEvery { settings.snapshotAsync() } returns SettingsSnapshot(providerId = "ollama", modelId = "llama-2")
        every { providers.listProviders() } returns listOf(ollama, codex)
        every { memory.snapshot() } returns MainAgentMemorySnapshot("", 0, 12_000, 0)
        coEvery { providers.discoverModels(match { it.id == "codex" }) } returns
            listOf(ProviderModel("codex-1"), ProviderModel("codex-2"))
        coEvery { providers.discoverModels(match { it.id == "ollama" }) } returns
            listOf(ProviderModel("llama-1"), ProviderModel("llama-2"))

        composeTestRule.setContent {
            MaterialTheme { providerSettingsOverlay(settings, memory, providers, onSettingsChanged = {}) }
        }
        composeTestRule.onNodeWithText("Provider settings loaded").assertExists()
        composeTestRule.onNodeWithText("Ollama").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Codex").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("codex-2").assertExists()

        composeTestRule.onNodeWithText("Codex").performClick()
        composeTestRule.onNodeWithText("Ollama").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("llama-2").assertExists()
        coVerify(exactly = 1) { providers.discoverModels(match { it.id == "codex" }) }
        coVerify(exactly = 1) { providers.discoverModels(match { it.id == "ollama" }) }
    }

    @Test
    fun `conversation settings save without rewriting an uncached provider catalog`() {
        val settings = mockk<SettingsPort>(relaxed = true)
        val providers = mockk<ProviderPort>(relaxed = true)
        val memory = mockk<MainAgentMemoryPort>()
        val current = SettingsSnapshot(providerId = "openai", modelId = "gpt-4o-mini")
        val profile =
            ProviderProfile(
                id = "openai",
                name = "OpenAI",
                adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                baseUrl = "https://api.openai.com",
                defaultModel = "gpt-4o-mini",
            )
        var settingsChanged = false
        coEvery { settings.snapshotAsync() } returns current
        every { settings.snapshot() } returns current
        every { providers.listProviders() } returns listOf(profile)
        every { memory.snapshot() } returns MainAgentMemorySnapshot("", 0, 12_000, 0)

        composeTestRule.setContent {
            MaterialTheme {
                providerSettingsOverlay(
                    settings,
                    memory,
                    providers,
                    onSettingsChanged = { settingsChanged = true },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Model instruction").performTextInput("Keep answers concise")
        composeTestRule.onNodeWithText("Save changes").performClick()
        composeTestRule.waitForIdle()

        verify {
            settings.save(
                match { snapshot -> snapshot.userModelInstruction == "Keep answers concise" },
                null,
            )
        }
        assertTrue(settingsChanged)
    }

    @Test
    fun `conversation setting steppers save their updated values`() {
        val settings = mockk<SettingsPort>(relaxed = true)
        val providers = mockk<ProviderPort>(relaxed = true)
        val memory = mockk<MainAgentMemoryPort>()
        val current = SettingsSnapshot(providerId = "ollama", modelId = "llama", maxParallelSubAgents = 3)
        val profile =
            ProviderProfile(
                id = "ollama",
                name = "Ollama",
                adapter = ProviderAdapter.OLLAMA,
                baseUrl = "http://localhost:11434",
                defaultModel = "llama",
                models = listOf(ProviderModel("llama")),
            )
        coEvery { settings.snapshotAsync() } returns current
        every { settings.snapshot() } returns current
        every { providers.listProviders() } returns listOf(profile)
        every { memory.snapshot() } returns MainAgentMemorySnapshot("", 0, 12_000, 0)

        composeTestRule.setContent {
            MaterialTheme { providerSettingsOverlay(settings, memory, providers, onSettingsChanged = {}) }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onAllNodesWithText("Increase")
            .get(0)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText("Save changes").performClick()
        composeTestRule.waitForIdle()

        verify {
            settings.save(
                match { snapshot -> snapshot.maxParallelSubAgents == 4 },
                null,
            )
        }
    }

    @Test
    fun `reset button restores unsaved conversation settings`() {
        val settings = mockk<SettingsPort>(relaxed = true)
        val providers = mockk<ProviderPort>(relaxed = true)
        val memory = mockk<MainAgentMemoryPort>()
        val current = SettingsSnapshot(providerId = "ollama", modelId = "llama")
        val profile =
            ProviderProfile(
                id = "ollama",
                name = "Ollama",
                adapter = ProviderAdapter.OLLAMA,
                baseUrl = "http://localhost:11434",
                defaultModel = "llama",
                models = listOf(ProviderModel("llama")),
            )
        coEvery { settings.snapshotAsync() } returns current
        every { providers.listProviders() } returns listOf(profile)
        every { memory.snapshot() } returns MainAgentMemorySnapshot("", 0, 12_000, 0)

        composeTestRule.setContent {
            MaterialTheme { providerSettingsOverlay(settings, memory, providers, onSettingsChanged = {}) }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Model instruction").performTextInput("Unsaved guidance")
        composeTestRule.onNodeWithText("Reset changes").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Model instruction").performTextInput("Reset verified")
        composeTestRule.onNodeWithContentDescription("Model instruction").assertTextEquals("Reset verified")
        verify(exactly = 0) { settings.save(any(), any()) }
    }

    @Test
    fun `refresh and favorite buttons update the staged model settings`() {
        val settings = mockk<SettingsPort>(relaxed = true)
        val providers = mockk<ProviderPort>(relaxed = true)
        val memory = mockk<MainAgentMemoryPort>()
        val current = SettingsSnapshot(providerId = "ollama", modelId = "llama")
        val profile =
            ProviderProfile(
                id = "ollama",
                name = "Ollama",
                adapter = ProviderAdapter.OLLAMA,
                baseUrl = "http://localhost:11434",
                defaultModel = "llama",
                models = listOf(ProviderModel("llama")),
            )
        coEvery { settings.snapshotAsync() } returns current
        every { settings.snapshot() } returns current
        every { providers.listProviders() } returns listOf(profile)
        every { memory.snapshot() } returns MainAgentMemorySnapshot("", 0, 12_000, 0)
        coEvery { providers.discoverModels(profile) } returns listOf(ProviderModel("llama"))

        composeTestRule.setContent {
            MaterialTheme { providerSettingsOverlay(settings, memory, providers, onSettingsChanged = {}) }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Refresh models").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        coVerify(exactly = 1) { providers.discoverModels(profile) }
        composeTestRule.onNodeWithContentDescription("Favorite").performClick()
        composeTestRule.onNodeWithText("Save changes").performClick()
        composeTestRule.waitForIdle()

        verify {
            settings.save(
                match { snapshot -> snapshot.favoriteModels == listOf("llama") },
                null,
            )
        }
    }

    @Test
    fun `provider remove and onboarding buttons invoke their actions`() {
        val settings = mockk<SettingsPort>(relaxed = true)
        val providers = mockk<ProviderPort>(relaxed = true)
        val memory = mockk<MainAgentMemoryPort>()
        val ollama =
            ProviderProfile(
                id = "ollama",
                name = "Ollama",
                adapter = ProviderAdapter.OLLAMA,
                baseUrl = "http://localhost:11434",
                defaultModel = "llama",
                models = listOf(ProviderModel("llama")),
            )
        val codex =
            ProviderProfile(
                id = "codex",
                name = "Codex",
                adapter = ProviderAdapter.CODEX_CLI,
                baseUrl = "",
                defaultModel = "codex-model",
                models = listOf(ProviderModel("codex-model")),
            )
        coEvery { settings.snapshotAsync() } returns SettingsSnapshot(providerId = "ollama", modelId = "llama")
        every { providers.listProviders() } returns listOf(ollama, codex)
        every { memory.snapshot() } returns MainAgentMemorySnapshot("", 0, 12_000, 0)
        var onboardingRequested = false

        composeTestRule.setContent {
            MaterialTheme {
                providerSettingsOverlay(
                    settings,
                    memory,
                    providers,
                    onSettingsChanged = {},
                    onRunOnboarding = { onboardingRequested = true },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Remove provider").performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Codex").assertExists()
        composeTestRule.onNodeWithText("Run onboarding again").performScrollTo().performClick()

        assertTrue(onboardingRequested)
        verify(exactly = 0) { settings.save(any(), any()) }
    }
}
