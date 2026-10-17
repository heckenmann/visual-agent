package de.heckenmann.visualagent.task

import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.provider.ModelStatus
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import io.mockk.every
import io.mockk.mockk
import org.springframework.beans.factory.ObjectProvider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskModelCapabilitiesTest {
    @Test
    fun `current explicit provider and authoritative capabilities govern eligibility`() {
        val catalog = mockk<ProviderCatalogService>()
        val reference = mockk<ObjectProvider<ProviderCatalogService>>()
        every { reference.getObject() } returns catalog
        val resolver = TaskModelCapabilities(reference)
        val agent = SubAgent("worker", "Worker", "Custom role", config = AgentConfig(provider = "chosen", model = "model"))
        var profile =
            ProviderProfile(
                "chosen",
                "Chosen",
                ProviderAdapter.OLLAMA,
                "http://localhost",
                defaultModel = "model",
                models = listOf(ProviderModelConfig("model", capabilitiesComplete = true)),
            )
        every { catalog.getProvider("chosen") } answers { profile }
        assertFalse(resolver.supportsTools(agent))
        profile = profile.copy(models = listOf(ProviderModelConfig("model", capabilities = setOf("tools"), capabilitiesComplete = true)))
        assertTrue(resolver.supportsTools(agent))
        profile = profile.copy(enabled = false)
        assertFalse(resolver.supportsTools(agent))
        profile = profile.copy(enabled = true, models = listOf(ProviderModelConfig("model")))
        assertTrue(resolver.supportsTools(agent))
        profile = profile.copy(models = listOf(ProviderModelConfig("model", status = ModelStatus.DISABLED)))
        assertFalse(resolver.supportsTools(agent))
        profile = profile.copy(models = emptyList(), modelBlacklist = setOf("model"))
        assertFalse(resolver.supportsTools(agent))
        every { catalog.getProvider("chosen") } returns null
        assertFalse(resolver.supportsTools(agent))
    }
}
