package de.heckenmann.visualagent.ui.settings

import de.heckenmann.visualagent.protocol.DirectProviderAuthMode
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderProfile
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ComposeDirectProviderDraftTest {
    @Test
    fun `authorization preserves unrelated profile edits and targets only its own profile`() {
        val profile = profile().copy(name = "Edited during sign-in", options = mapOf("reasoningEffort" to "high"))
        val other = profile().copy(id = "other")
        val draft = ProviderSettingsDraft(providers = listOf(profile, other))

        val changed = draft.withDirectCredentialReference("direct", "registration")

        assertEquals(profile.copy(credentialReference = "registration", apiKey = ""), changed.providers.first())
        assertEquals(other, changed.providers.last())
    }

    @Test
    fun `late authorization cannot attach an OAuth registration after an explicit switch to API key mode`() {
        val draft = ProviderSettingsDraft(providers = listOf(profile().copy(directAuthMode = DirectProviderAuthMode.API_KEY)))

        assertEquals(draft, draft.withDirectCredentialReference("direct", "registration"))
    }

    private fun profile() =
        ProviderProfile(
            "direct",
            "Direct",
            ProviderAdapter.CODEX_DIRECT,
            "https://api.openai.com",
            directAuthMode = DirectProviderAuthMode.CHATGPT_OAUTH,
        )
}
