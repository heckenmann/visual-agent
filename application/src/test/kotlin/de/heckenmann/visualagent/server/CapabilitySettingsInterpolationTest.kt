package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.protocol.ProviderConfiguration
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import io.mockk.every
import io.mockk.mockk
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Exercises configuration validation, persistence and subsequent interpolation as one workflow. */
class CapabilitySettingsInterpolationTest {
    @Test
    fun `invalid selection is rejected before saving and corrected selection reaches interpolation`() {
        KnowledgeDbTestFactory.create("jdbc:h2:mem:capability-settings-interpolation").use { db ->
            val catalog = ProviderCatalogService(db.preferenceStore)
            val model =
                ProviderModelConfig("legacy", capabilities = setOf("vision"), capabilitiesComplete = true, capabilitiesOverridden = true)
            catalog.saveProvider(ProviderProfile("custom", "Custom", ProviderAdapter.CODEX_CLI, "", models = listOf(model)))
            val provider = mockk<LLMProvider>()
            every { provider.chatReactive(any<ChatRequestContext>()) } returns
                Mono.just(ChatResponse("legacy", Message("assistant", "Clarified prompt."), true))
            val providerPort = SpringProviderPort(catalog, provider)
            val config = AppConfigBean(db.preferenceStore)
            val settings = SpringSettingsPort(config, catalog, db.mainAgentLongTermMemoryStore, io.mockk.mockk(relaxed = true))
            val profiles = providerPort.listProviders()
            val selection = ProviderConfiguration(profiles, "custom", model.id)
            val snapshot = SettingsSnapshot(providerId = "custom", modelId = model.id, automaticPromptInterpolation = true)
            val previousProvider = catalog.activeProviderId()
            assertFailsWith<IllegalArgumentException> { settings.save(snapshot, selection) }
            assertEquals(previousProvider, catalog.activeProviderId())
            assertEquals(false, config.automaticPromptInterpolation)
            val corrected =
                profiles.map { profile ->
                    if (profile.id !=
                        "custom"
                    ) {
                        profile
                    } else {
                        profile.copy(models = profile.models.map { it.copy(capabilityOverrides = mapOf("chat" to true)) })
                    }
                }
            settings.save(snapshot, selection.copy(providers = corrected))
            val reloaded = ProviderCatalogService(db.preferenceStore)
            assertEquals(
                mapOf("chat" to true),
                reloaded
                    .getProvider("custom")!!
                    .models
                    .single()
                    .capabilityOverrides,
            )
            assertEquals(
                "Clarified prompt.",
                PromptInterpolationService(
                    provider,
                    reloaded,
                    db.conversationStore,
                    config,
                ).interpolate("clarify it", CancellationToken()).block(),
            )
            val invalidExplicitInterpolation =
                snapshot.copy(
                    promptInterpolationProviderId = "custom",
                    promptInterpolationModelId = model.id,
                )
            assertFailsWith<IllegalArgumentException> { settings.save(invalidExplicitInterpolation, selection) }
            assertEquals(
                mapOf("chat" to true),
                catalog
                    .getProvider("custom")!!
                    .models
                    .single()
                    .capabilityOverrides,
            )
        }
    }
}
