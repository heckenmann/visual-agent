package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.provider.DirectProviderAuthorization
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.protocol.CredentialUpdate
import de.heckenmann.visualagent.protocol.DirectProviderAuthMode
import de.heckenmann.visualagent.protocol.OnboardingProviderDraft
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DirectOnboardingProfileTest {
    @Test
    fun `finishing a staged OAuth profile saves its validated registration`() =
        runTest {
            KnowledgeDbTestFactory.create("jdbc:h2:mem:staged-direct-onboarding").use { db ->
                val catalog = ProviderCatalogService(db.preferenceStore)
                val authorization = mockk<DirectProviderAuthorization>()
                val provider = mockk<LLMProvider>()
                every { authorization.saveStaged(any(), any()) } answers { secondArg<(ProviderProfile?) -> Mono<Void>>()(null) }
                every { provider.getModelConfigsReactive(any<ProviderProfile>()) } returns
                    Mono.just(listOf(ProviderModelConfig("verified-model")))
                every { provider.chatReactive(any<ChatRequestContext>()) } returns
                    Mono.just(ChatResponse("verified-model", Message("assistant", "READY"), true))
                val port = SpringOnboardingPort(db.preferenceStore, catalog, provider, mockk(), authorization)
                val draft =
                    OnboardingProviderDraft(
                        "direct",
                        "Direct",
                        ProviderAdapter.CODEX_DIRECT,
                        "https://api.openai.com",
                        directAuthMode = DirectProviderAuthMode.CHATGPT_OAUTH,
                        credentialReference = "staged-registration",
                    )

                val validation = port.validate(draft, "verified-model")
                assertTrue(catalog.listProviders().isEmpty())
                port.finish(draft, ProviderModel("verified-model"), checkNotNull(validation.validationFingerprint))

                assertEquals(draft.credentialReference, catalog.listProviders().single().credentialReference)
                assertEquals("verified-model", catalog.activeModelId())
                verify(exactly = 1) { authorization.saveStaged(any(), any()) }
            }
        }

    @Test
    fun `OAuth onboarding discovery preserves the selected registration without forwarding an API key`() =
        runTest {
            val catalog = mockk<ProviderCatalogService>()
            val provider = mockk<LLMProvider>()
            val port = SpringOnboardingPort(mockk(), catalog, provider, mockk())
            every { catalog.getProvider("direct") } returns null
            every { provider.getModelConfigsReactive(any<ProviderProfile>()) } returns Mono.just(emptyList())
            val draft =
                OnboardingProviderDraft(
                    "direct",
                    "Direct",
                    ProviderAdapter.CODEX_DIRECT,
                    "https://api.openai.com",
                    credential = CredentialUpdate.Replace("must-not-be-used"),
                    directAuthMode = DirectProviderAuthMode.CHATGPT_OAUTH,
                    credentialReference = "registration",
                )

            port.discoverModels(draft)

            verify {
                provider.getModelConfigsReactive(
                    match { profile ->
                        profile.directAuthMode == de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode.CHATGPT_OAUTH &&
                            profile.credentialReference == "registration" &&
                            profile.apiKey.isEmpty()
                    },
                )
            }
        }

    @Test
    fun `safe OAuth onboarding view reports registration presence without tokens`() {
        val catalog = mockk<ProviderCatalogService>()
        val port = SpringOnboardingPort(mockk(), catalog, mockk(), mockk())
        every { catalog.listProviders() } returns
            listOf(
                ProviderProfile(
                    "direct",
                    "Direct",
                    de.heckenmann.visualagent.agent.provider.ProviderAdapter.CODEX_DIRECT,
                    "https://api.openai.com",
                    directAuthMode = de.heckenmann.visualagent.agent.provider.DirectProviderAuthMode.CHATGPT_OAUTH,
                    credentialReference = "registration",
                ),
            )

        val view = port.providers().single()

        assertTrue(view.credentialConfigured)
        assertEquals(DirectProviderAuthMode.CHATGPT_OAUTH, view.directAuthMode)
        assertEquals("registration", view.credentialReference)
    }
}
