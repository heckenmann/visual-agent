package de.heckenmann.visualagent.ui.onboarding

import de.heckenmann.visualagent.protocol.OnboardingProviderDraft
import de.heckenmann.visualagent.protocol.ProviderAdapter
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies safe staged provider-type transitions in the onboarding editor. */
class OnboardingProviderDraftTest {
    @Test
    fun `switching provider adapter selects its default endpoint`() {
        val updated =
            OnboardingProviderDraft(
                id = "provider",
                name = "Provider",
                adapter = ProviderAdapter.OLLAMA,
                baseUrl = "http://localhost:11434",
            ).withAdapter(ProviderAdapter.OPENAI_COMPATIBLE)

        assertEquals(ProviderAdapter.OPENAI_COMPATIBLE, updated.adapter)
        assertEquals("https://api.openai.com", updated.baseUrl)
        assertEquals("http://localhost:11434", ProviderAdapter.OLLAMA.defaultEndpoint())
    }

    @Test
    fun `switching to Codex CLI clears the incompatible HTTP endpoint`() {
        val updated =
            OnboardingProviderDraft(
                id = "provider",
                name = "Provider",
                adapter = ProviderAdapter.OLLAMA,
                baseUrl = "http://localhost:11434",
            ).withAdapter(ProviderAdapter.CODEX_CLI)

        assertEquals(ProviderAdapter.CODEX_CLI, updated.adapter)
        assertEquals("", updated.baseUrl)
    }
}
