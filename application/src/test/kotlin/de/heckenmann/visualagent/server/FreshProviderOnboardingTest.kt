package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderConfiguration
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.protocol.OnboardingProviderDraft
import de.heckenmann.visualagent.protocol.OnboardingStatus
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import reactor.core.publisher.Mono
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Exercises onboarding against a fresh catalog and a reopened server database. */
class FreshProviderOnboardingTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `fresh database onboarding exposes an empty catalog and skipping keeps it empty after restart`() =
        runTest {
            KnowledgeDbTestFactory.create(directory.resolve("database").toString()).use { db ->
                val catalog = ProviderCatalogService(db.preferenceStore)
                val provider = mockk<LLMProvider>()
                every { provider.getModelConfigsReactive(any<ProviderProfile>()) } returns
                    Mono.just(listOf(ProviderModelConfig("staged-model")))
                val port = SpringOnboardingPort(db.preferenceStore, catalog, provider, mockk())
                val draft =
                    OnboardingProviderDraft(
                        id = "discarded-profile",
                        name = "Discarded profile",
                        adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                        baseUrl = "https://api.example",
                    )

                assertEquals(OnboardingStatus.NOT_STARTED, port.state().status)
                assertTrue(port.providers().isEmpty())
                assertEquals(listOf("staged-model"), port.discoverModels(draft).map { it.id })
                assertTrue(catalog.listProviders().isEmpty())

                port.dismiss()
            }
            KnowledgeDbTestFactory.create(directory.resolve("database").toString()).use { db ->
                val restoredCatalog = ProviderCatalogService(db.preferenceStore)
                val restoredPort = SpringOnboardingPort(db.preferenceStore, restoredCatalog, mockk(), mockk())
                assertEquals(OnboardingStatus.DISMISSED, restoredPort.state().status)
                assertTrue(restoredPort.providers().isEmpty())
            }
        }

    @Test
    fun `fresh database saves only the first provider explicitly committed through onboarding`() =
        runTest {
            KnowledgeDbTestFactory.create(directory.resolve("database").toString()).use { db ->
                val catalog = ProviderCatalogService(db.preferenceStore)
                val provider = mockk<LLMProvider>()
                every { provider.getModelConfigsReactive(any<ProviderProfile>()) } returns
                    Mono.just(listOf(ProviderModelConfig("verified-model")))
                every { provider.chatReactive(any<ChatRequestContext>()) } returns
                    Mono.just(ChatResponse("verified-model", Message("assistant", "READY"), true))
                val port = SpringOnboardingPort(db.preferenceStore, catalog, provider, mockk())
                val draft =
                    OnboardingProviderDraft(
                        id = "first-profile",
                        name = "First profile",
                        adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                        baseUrl = "https://api.example",
                    )

                assertTrue(port.providers().isEmpty())
                assertEquals(listOf("verified-model"), port.discoverModels(draft).map { it.id })
                assertTrue(catalog.listProviders().isEmpty())
                val validation = port.validate(draft, "verified-model")
                assertTrue(catalog.listProviders().isEmpty())
                port.finish(draft, ProviderModel("verified-model"), checkNotNull(validation.validationFingerprint))

                assertEquals(listOf("first-profile"), catalog.listProviders().map(ProviderProfile::id))
            }
            KnowledgeDbTestFactory.create(directory.resolve("database").toString()).use { db ->
                val restored = ProviderCatalogService(db.preferenceStore)
                assertEquals(listOf("first-profile"), restored.listProviders().map(ProviderProfile::id))
                assertEquals("verified-model", restored.activeModelId())
            }
        }

    @Test
    fun `finish does not overwrite a credential rotated during readiness validation`() =
        runTest {
            KnowledgeDbTestFactory.create(directory.resolve("rotated-credential").toString()).use { db ->
                val catalog = ProviderCatalogService(db.preferenceStore)
                val original =
                    ProviderProfile(
                        id = "existing-profile",
                        name = "Existing profile",
                        adapter = de.heckenmann.visualagent.agent.provider.ProviderAdapter.OPENAI_COMPATIBLE,
                        baseUrl = "https://api.example",
                        apiKey = "original-key",
                        models = listOf(ProviderModelConfig("verified-model")),
                    )
                catalog
                    .replaceConfigurationReactive(ProviderConfiguration(listOf(original), original.id, "verified-model"))
                    .awaitSingleOrNull()

                val readinessCalls = AtomicInteger()
                val provider = mockk<LLMProvider>()
                every { provider.getModelConfigsReactive(any<ProviderProfile>()) } returns
                    Mono.just(listOf(ProviderModelConfig("verified-model")))
                every { provider.chatReactive(any<ChatRequestContext>()) } answers {
                    Mono.defer {
                        if (readinessCalls.incrementAndGet() == 2) {
                            val rotated = original.copy(apiKey = "rotated-key")
                            catalog
                                .replaceConfigurationReactive(ProviderConfiguration(listOf(rotated), rotated.id, "verified-model"))
                                .thenReturn(ChatResponse("verified-model", Message("assistant", "READY"), true))
                        } else {
                            Mono.just(ChatResponse("verified-model", Message("assistant", "READY"), true))
                        }
                    }
                }
                val port = SpringOnboardingPort(db.preferenceStore, catalog, provider, mockk())
                val draft =
                    OnboardingProviderDraft(
                        id = original.id,
                        name = original.name,
                        adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                        baseUrl = original.baseUrl,
                    )

                val validation = port.validate(draft, "verified-model")
                assertFailsWith<IllegalStateException> {
                    port.finish(draft, ProviderModel("verified-model"), checkNotNull(validation.validationFingerprint))
                }

                assertEquals("rotated-key", catalog.getProvider(original.id)?.apiKey)
            }
        }
}
