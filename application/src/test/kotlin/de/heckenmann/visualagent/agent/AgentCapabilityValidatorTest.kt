package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.provider.ModelCapability
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.provider.ResolvedModelConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Rejects specialized or chat assignments that cannot run on the chosen provider. */
class AgentCapabilityValidatorTest {
    @Test
    fun `explicit chat assignment rejects an embedding only model`() {
        val catalog = mockk<ProviderCatalogService>()
        val model =
            ProviderModelConfig("embed", capabilities = setOf("embedding"), capabilitiesComplete = true)
        val profile =
            ProviderProfile(
                "remote",
                "Remote",
                ProviderAdapter.OPENAI_COMPATIBLE,
                "https://example.test",
                models = listOf(model),
            )
        every { catalog.resolve("remote", "embed", any(), any()) } returns
            ResolvedModelConfig(profile, model, null, emptyMap())

        val failure =
            assertFailsWith<IllegalArgumentException> {
                AgentCapabilityValidator(catalog).validate(
                    AgentConfig(capabilityModels = mapOf(ModelCapability.CHAT to "remote::embed")),
                )
            }

        assertTrue(failure.message.orEmpty().contains("CHAT"))
        verify(exactly = 1) { catalog.resolve("remote", "embed", any(), any()) }
    }

    @Test
    fun `valid explicit chat assignment is accepted`() {
        val catalog = mockk<ProviderCatalogService>()
        val model = ProviderModelConfig("chat", capabilities = setOf("chat"), capabilitiesComplete = true)
        val profile =
            ProviderProfile(
                "remote",
                "Remote",
                ProviderAdapter.OPENAI_COMPATIBLE,
                "https://example.test",
                models = listOf(model),
            )
        every { catalog.resolve("remote", "chat", any(), any()) } returns
            ResolvedModelConfig(profile, model, null, emptyMap())

        AgentCapabilityValidator(catalog).validate(
            AgentConfig(capabilityModels = mapOf(ModelCapability.CHAT to "remote::chat")),
        )
        verify(exactly = 1) { catalog.resolve("remote", "chat", any(), any()) }
    }

    @Test
    fun `inherited chat model remains valid without catalog resolution`() {
        val catalog = mockk<ProviderCatalogService>()
        AgentCapabilityValidator(catalog).validate(AgentConfig())
        verify(exactly = 0) { catalog.resolve(any(), any(), any(), any()) }
    }
}
