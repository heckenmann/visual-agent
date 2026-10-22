package de.heckenmann.visualagent.agent.provider

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProviderAuthenticationPersistenceTest {
    @Test
    fun `legacy profiles retain API key authentication without a credential reference`() {
        val profile =
            Json.decodeFromString<ProviderProfile>(
                """{"id":"existing","name":"Existing","adapter":"OPENAI_COMPATIBLE","baseUrl":"https://api.openai.com"}""",
            )

        assertEquals(DirectProviderAuthMode.API_KEY, profile.directAuthMode)
        assertEquals(null, profile.credentialReference)
    }

    @Test
    fun `explicit OAuth mode round trips independently of API key presence`() {
        val profile =
            ProviderProfile(
                "oauth",
                "OAuth",
                ProviderAdapter.OPENAI_COMPATIBLE,
                "https://api.openai.com",
                directAuthMode = DirectProviderAuthMode.CHATGPT_OAUTH,
                credentialReference = "registration-1",
            )

        val restored = Json.decodeFromString<ProviderProfile>(Json.encodeToString(profile))

        assertEquals(profile, restored)
        assertEquals(DirectProviderAuthMode.CHATGPT_OAUTH, restored.directAuthMode)
        assertEquals("", restored.apiKey)
    }
}
