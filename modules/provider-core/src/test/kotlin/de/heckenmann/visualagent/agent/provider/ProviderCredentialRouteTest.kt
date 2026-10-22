package de.heckenmann.visualagent.agent.provider

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProviderCredentialRouteTest {
    @Test
    fun `OAuth persistence removes an unrelated API key without changing auth mode`() {
        val profile = profile().copy(directAuthMode = DirectProviderAuthMode.CHATGPT_OAUTH)

        val normalized = profile.withSelectedCredentialRoute()

        assertEquals(DirectProviderAuthMode.CHATGPT_OAUTH, normalized.directAuthMode)
        assertEquals("", normalized.apiKey)
        assertEquals("registration", normalized.credentialReference)
    }

    @Test
    fun `API key persistence removes OAuth reference without changing the key`() {
        val normalized = profile().withSelectedCredentialRoute()

        assertEquals("synthetic-key", normalized.apiKey)
        assertNull(normalized.credentialReference)
    }

    @Test
    fun `generic providers do not retain direct OAuth configuration`() {
        val normalized =
            profile()
                .copy(adapter = ProviderAdapter.OLLAMA, directAuthMode = DirectProviderAuthMode.CHATGPT_OAUTH)
                .withSelectedCredentialRoute()

        assertEquals(DirectProviderAuthMode.API_KEY, normalized.directAuthMode)
        assertEquals("synthetic-key", normalized.apiKey)
        assertNull(normalized.credentialReference)
    }

    private fun profile() =
        ProviderProfile(
            "direct",
            "Direct",
            ProviderAdapter.CODEX_DIRECT,
            "https://api.openai.com",
            "synthetic-key",
            credentialReference = "registration",
        )
}
