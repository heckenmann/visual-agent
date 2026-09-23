package de.heckenmann.visualagent.ui.settings

import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.ProviderProfile
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies provider state normalization used by the settings panel. */
class ComposeProtocolSettingsSupportTest {
    @Test
    fun `preferred provider and active model fall back to enabled catalog values`() {
        val providerPort = mockk<ProviderPort>()
        val providers =
            listOf(
                ProviderProfile("disabled", "Disabled", ProviderAdapter.OLLAMA, "", enabled = false),
                ProviderProfile("openai", "OpenAI", ProviderAdapter.OPENAI_COMPATIBLE, "https://example.test"),
            )
        every { providerPort.listProviders() } returns providers
        every { providerPort.selectableModels("openai") } returns listOf(ProviderModel("gpt", "GPT"))
        every { providerPort.activeModelId() } returns "missing"

        val state = readProviderSettings(providerPort, preferredProviderId = "disabled")

        assertEquals(listOf("disabled", "openai"), state.providers.map(ProviderProfile::id))
        assertEquals("openai", state.providerId)
        assertEquals("gpt", state.modelId)
        assertEquals(listOf("gpt"), state.models.map(ProviderModel::id))
    }

    @Test
    fun `disabling the selected provider selects an enabled fallback`() {
        val draft =
            ProviderSettingsDraft(
                providers =
                    listOf(
                        ProviderProfile(
                            "active",
                            "Active",
                            ProviderAdapter.OLLAMA,
                            "",
                            models = listOf(ProviderModel("old")),
                        ),
                        ProviderProfile(
                            "fallback",
                            "Fallback",
                            ProviderAdapter.OPENAI_COMPATIBLE,
                            "",
                            models = listOf(ProviderModel("new")),
                        ),
                    ),
                providerId = "active",
                modelId = "old",
            )

        val normalized = draft.upsert(draft.providers.first().copy(enabled = false))

        assertEquals("fallback", normalized.providerId)
        assertEquals("new", normalized.modelId)
    }

    @Test
    fun `model refresh restores preferred model only for the selected provider`() {
        val draft =
            ProviderSettingsDraft(
                providers =
                    listOf(
                        ProviderProfile(
                            "openai",
                            "OpenAI",
                            ProviderAdapter.OPENAI_COMPATIBLE,
                            "",
                            defaultModel = "gpt",
                            models = listOf(ProviderModel("gpt")),
                        ),
                        ProviderProfile(
                            "codex",
                            "Codex",
                            ProviderAdapter.CODEX_CLI,
                            "",
                            defaultModel = "codex-2",
                            models = listOf(ProviderModel("codex-1")),
                        ),
                    ),
                providerId = "codex",
                modelId = "codex-1",
            )

        val refreshed = draft.withModels("codex", listOf(ProviderModel("codex-1"), ProviderModel("codex-2")), "codex-2")
        val staleOtherProvider = refreshed.withModels("openai", listOf(ProviderModel("gpt-new")), "gpt-new")

        assertEquals("codex-2", refreshed.modelId)
        assertEquals("codex-2", staleOtherProvider.modelId)
        assertEquals(
            listOf("gpt-new"),
            staleOtherProvider.providers
                .first()
                .models
                .map(ProviderModel::id),
        )
    }
}
