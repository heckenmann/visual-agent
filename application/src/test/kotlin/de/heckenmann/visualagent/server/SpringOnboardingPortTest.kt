package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.provider.ModelStatus
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.knowledge.PreferenceStore
import de.heckenmann.visualagent.protocol.OnboardingProviderDraft
import de.heckenmann.visualagent.protocol.OnboardingStatus
import de.heckenmann.visualagent.protocol.OnboardingValidationCode
import de.heckenmann.visualagent.protocol.ProviderModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.springframework.transaction.support.TransactionTemplate
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import de.heckenmann.visualagent.protocol.ProviderAdapter as ProtocolProviderAdapter

/** Verifies the safe, server-owned parts of the onboarding protocol adapter. */
class SpringOnboardingPortTest {
    private val preferences = mockk<PreferenceStore>()
    private val catalog = mockk<ProviderCatalogService>()
    private val port = SpringOnboardingPort(preferences, catalog, mockk<LLMProvider>(), mockk<TransactionTemplate>())

    @Test
    fun `provider views do not return stored credentials`() {
        every { catalog.listProviders() } returns
            listOf(ProviderProfile("openai", "OpenAI", ProviderAdapter.OPENAI_COMPATIBLE, "https://api.example", "secret"))

        val profile = port.providers().single()

        assertTrue(profile.credentialConfigured)
        assertEquals("https://api.example", profile.baseUrl)
        assertTrue(profile.toString().contains("secret").not())
    }

    @Test
    fun `dismiss changes only the onboarding status`() {
        every { preferences.setPreference(any(), any()) } returns Unit

        port.dismiss()

        verify { preferences.setPreference("ui.onboarding.v1", OnboardingStatus.DISMISSED.name) }
    }

    @Test
    fun `invalid persisted status is surfaced as recoverable startup failure`() {
        every { preferences.getPreference("ui.onboarding.v1") } returns "unknown"

        assertFailsWith<IllegalStateException> { port.state() }
    }

    @Test
    fun `validation probes the exact staged provider and selected model`() =
        runTest {
            val provider = mockk<LLMProvider>()
            val port = SpringOnboardingPort(preferences, catalog, provider, mockk())
            val draft =
                OnboardingProviderDraft(
                    id = "openai",
                    name = "OpenAI",
                    adapter = ProtocolProviderAdapter.OPENAI_COMPATIBLE,
                    baseUrl = "https://api.example",
                    defaultModel = "verified-model",
                )
            every { catalog.getProvider("openai") } returns null
            coEvery { provider.getModelConfigs(any<ProviderProfile>()) } returns listOf(ProviderModelConfig("verified-model"))
            coEvery { provider.chat(any<ChatRequestContext>()) } returns
                ChatResponse("verified-model", Message("assistant", "READY"), true)

            val result = port.validate(draft, "verified-model")

            assertTrue(result.success)
            coVerify {
                provider.chat(
                    match<ChatRequestContext> { request ->
                        request.model == "verified-model" && request.providerProfile?.id == "openai" && request.parameters.maxTokens == 8
                    },
                )
            }
        }

    @Test
    fun `validation reports incomplete http profile as invalid configuration`() =
        runTest {
            val provider = mockk<LLMProvider>()
            val port = SpringOnboardingPort(preferences, catalog, provider, mockk())
            val draft =
                OnboardingProviderDraft(
                    id = "openai",
                    name = "OpenAI",
                    adapter = ProtocolProviderAdapter.OPENAI_COMPATIBLE,
                    baseUrl = "",
                )
            every { catalog.getProvider("openai") } returns null

            val result = port.validate(draft, "selected-model")

            assertEquals(OnboardingValidationCode.INVALID_CONFIGURATION, result.code)
            coVerify(exactly = 0) { provider.getModelConfigs(any<ProviderProfile>()) }
        }

    @Test
    fun `finish commits catalog selection and completion together after readiness succeeds`() =
        runTest {
            val provider = mockk<LLMProvider>()
            val transactionTemplate = mockk<TransactionTemplate>()
            val port = SpringOnboardingPort(preferences, catalog, provider, transactionTemplate)
            val draft =
                OnboardingProviderDraft(
                    id = "openai",
                    name = "OpenAI",
                    adapter = ProtocolProviderAdapter.OPENAI_COMPATIBLE,
                    baseUrl = "https://api.example",
                )
            every { catalog.getProvider("openai") } returns null
            every { catalog.listProviders() } returns emptyList()
            every { catalog.replaceConfiguration(any()) } returns Unit
            every { preferences.setPreference(any(), any()) } returns Unit
            every { transactionTemplate.executeWithoutResult(any()) } answers {
                firstArg<Consumer<org.springframework.transaction.TransactionStatus>>().accept(mockk())
            }
            coEvery { provider.getModelConfigs(any<ProviderProfile>()) } returns listOf(ProviderModelConfig("verified-model"))
            coEvery { provider.chat(any<ChatRequestContext>()) } returns
                ChatResponse("verified-model", Message("assistant", "READY"), true)

            val validation = port.validate(draft, "verified-model")
            port.finish(draft, ProviderModel("verified-model"), checkNotNull(validation.validationFingerprint))

            verify {
                catalog.replaceConfiguration(
                    match { configuration ->
                        configuration.providerId == "openai" && configuration.modelId == "verified-model"
                    },
                )
                preferences.setPreference("ui.onboarding.v1", OnboardingStatus.COMPLETED.name)
            }
        }

    @Test
    fun `finish persists server-discovered model metadata instead of client-provided metadata`() =
        runTest {
            val provider = mockk<LLMProvider>()
            val transactionTemplate = mockk<TransactionTemplate>()
            val port = SpringOnboardingPort(preferences, catalog, provider, transactionTemplate)
            val draft =
                OnboardingProviderDraft(
                    id = "openai",
                    name = "OpenAI",
                    adapter = ProtocolProviderAdapter.OPENAI_COMPATIBLE,
                    baseUrl = "https://api.example",
                )
            val discovered = ProviderModelConfig("verified-model", name = "Verified", contextLimit = 131072)
            every { catalog.getProvider("openai") } returns null
            every { catalog.listProviders() } returns emptyList()
            every { catalog.replaceConfiguration(any()) } returns Unit
            every { preferences.setPreference(any(), any()) } returns Unit
            every { transactionTemplate.executeWithoutResult(any()) } answers {
                firstArg<Consumer<org.springframework.transaction.TransactionStatus>>().accept(mockk())
            }
            coEvery { provider.getModelConfigs(any<ProviderProfile>()) } returns listOf(discovered)
            coEvery { provider.chat(any<ChatRequestContext>()) } returns
                ChatResponse("verified-model", Message("assistant", "READY"), true)

            val validation = port.validate(draft, "verified-model")
            port.finish(
                draft,
                ProviderModel("verified-model", name = "Injected", contextLimit = 1),
                checkNotNull(validation.validationFingerprint),
            )

            verify {
                catalog.replaceConfiguration(
                    match { configuration ->
                        configuration.providers
                            .single()
                            .models
                            .single()
                            .name == "Verified" &&
                            configuration.providers
                                .single()
                                .models
                                .single()
                                .contextLimit == 131072
                    },
                )
            }
        }

    @Test
    fun `finish preserves existing provider model configuration`() =
        runTest {
            val provider = mockk<LLMProvider>()
            val transactionTemplate = mockk<TransactionTemplate>()
            val port = SpringOnboardingPort(preferences, catalog, provider, transactionTemplate)
            val configured =
                ProviderProfile(
                    id = "openai",
                    name = "OpenAI",
                    adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                    baseUrl = "https://api.example",
                    models =
                        listOf(
                            ProviderModelConfig(
                                id = "selected-model",
                                options = mapOf("temperature" to "0.2"),
                                variants = mapOf("fast" to mapOf("reasoning" to "low")),
                                contextLimit = 65536,
                            ),
                            ProviderModelConfig(id = "other-model", contextLimit = 32768),
                        ),
                )
            val draft =
                OnboardingProviderDraft(
                    id = "openai",
                    name = "OpenAI",
                    adapter = ProtocolProviderAdapter.OPENAI_COMPATIBLE,
                    baseUrl = "https://api.example",
                )
            every { catalog.getProvider("openai") } returns configured
            every { catalog.listProviders() } returns listOf(configured)
            every { catalog.replaceConfiguration(any()) } returns Unit
            every { preferences.setPreference(any(), any()) } returns Unit
            every { transactionTemplate.executeWithoutResult(any()) } answers {
                firstArg<Consumer<org.springframework.transaction.TransactionStatus>>().accept(mockk())
            }
            coEvery { provider.getModelConfigs(any<ProviderProfile>()) } returns listOf(ProviderModelConfig("selected-model"))
            coEvery { provider.chat(any<ChatRequestContext>()) } returns
                ChatResponse("selected-model", Message("assistant", "READY"), true)

            val validation = port.validate(draft, "selected-model")
            port.finish(draft, ProviderModel("selected-model"), checkNotNull(validation.validationFingerprint))

            verify {
                catalog.replaceConfiguration(
                    match { configuration ->
                        val models =
                            configuration.providers
                                .single()
                                .models
                                .associateBy { it.id }
                        models.keys == setOf("selected-model", "other-model") &&
                            models.getValue("selected-model").options == mapOf("temperature" to "0.2") &&
                            models.getValue("selected-model").variants == mapOf("fast" to mapOf("reasoning" to "low")) &&
                            models.getValue("selected-model").contextLimit == 65536 &&
                            models.getValue("other-model").contextLimit == 32768
                    },
                )
            }
        }

    @Test
    fun `model discovery preserves provider metadata`() =
        runTest {
            val provider = mockk<LLMProvider>()
            val port = SpringOnboardingPort(preferences, catalog, provider, mockk())
            val draft =
                OnboardingProviderDraft(
                    id = "openai",
                    name = "OpenAI",
                    adapter = ProtocolProviderAdapter.OPENAI_COMPATIBLE,
                    baseUrl = "https://api.example",
                )
            every { catalog.getProvider("openai") } returns null
            coEvery { provider.getModelConfigs(any<ProviderProfile>()) } returns
                listOf(
                    ProviderModelConfig(
                        id = "vision-model",
                        name = "Vision Model",
                        contextLimit = 131072,
                        outputLimit = 8192,
                        capabilities = setOf("vision"),
                    ),
                )

            val model = port.discoverModels(draft).single()

            assertEquals("Vision Model", model.name)
            assertEquals(131072, model.contextLimit)
            assertEquals(8192, model.outputLimit)
            assertEquals(setOf("vision"), model.capabilities)
        }

    @Test
    fun `model discovery excludes disabled models`() =
        runTest {
            val provider = mockk<LLMProvider>()
            val port = SpringOnboardingPort(preferences, catalog, provider, mockk())
            val draft =
                OnboardingProviderDraft(
                    id = "openai",
                    name = "OpenAI",
                    adapter = ProtocolProviderAdapter.OPENAI_COMPATIBLE,
                    baseUrl = "https://api.example",
                )
            every { catalog.getProvider("openai") } returns null
            coEvery { provider.getModelConfigs(any<ProviderProfile>()) } returns
                listOf(
                    ProviderModelConfig("available"),
                    ProviderModelConfig("disabled", status = ModelStatus.DISABLED),
                )

            assertEquals(listOf("available"), port.discoverModels(draft).map(ProviderModel::id))
        }
}
