package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.ModelDetails
import de.heckenmann.visualagent.agent.ShowResponse
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.protocol.ModelStatus
import de.heckenmann.visualagent.protocol.ProviderAdapter
import de.heckenmann.visualagent.protocol.ProviderProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import de.heckenmann.visualagent.agent.provider.ModelStatus as ApplicationModelStatus
import de.heckenmann.visualagent.agent.provider.ProviderAdapter as ApplicationProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderProfile as ApplicationProviderProfile

/** Verifies provider catalog and model discovery mapping at the server boundary. */
class SpringProviderPortTest {
    private val catalog = mockk<ProviderCatalogService>(relaxed = true)
    private val provider = mockk<LLMProvider>(relaxed = true)
    private val port = SpringProviderPort(catalog, provider)
    private val applicationProfile =
        ApplicationProviderProfile(
            id = "ollama",
            name = "Ollama",
            adapter = ApplicationProviderAdapter.OLLAMA,
            baseUrl = "http://localhost:11434",
            defaultModel = "llama3",
            options = mapOf("keep_alive" to "5m"),
            models =
                listOf(
                    ProviderModelConfig(
                        id = "llama3",
                        name = "Llama 3",
                        status = ApplicationModelStatus.ACTIVE,
                        variants = mapOf("fast" to mapOf("temperature" to "0.2")),
                        contextLimit = 8192,
                        outputLimit = 2048,
                        capabilities = setOf("tools"),
                    ),
                ),
        )

    @Test
    fun `catalog operations map profiles and delegate changes`() {
        every { catalog.listProviders() } returns listOf(applicationProfile)
        every { catalog.enabledProviders() } returns listOf(applicationProfile)
        every { catalog.getProvider("ollama") } returns applicationProfile
        every { catalog.selectableModels("ollama") } returns applicationProfile.models
        every { catalog.activeProviderId() } returns "ollama"
        every { catalog.activeModelId() } returns "llama3"
        every { catalog.deleteProvider("ollama") } returns true

        assertEquals("ollama", port.listProviders().single().id)
        assertEquals("Ollama", port.enabledProviders().single().name)
        assertEquals(ProviderAdapter.OLLAMA, port.getProvider("ollama")?.adapter)
        assertEquals(ModelStatus.ACTIVE, port.selectableModels("ollama").single().status)
        assertEquals(8192, port.selectableModels("ollama").single().contextLimit)
        assertEquals("ollama", port.activeProviderId())
        assertEquals("llama3", port.activeModelId())
        assertEquals(true, port.deleteProvider("ollama"))

        port.setActiveProvider("ollama")
        port.setActiveSelection("ollama", "llama3")
        port.saveProvider(port.getProvider("ollama")!!)

        verify { catalog.setActiveProvider("ollama") }
        verify { catalog.setActiveSelection("ollama", "llama3") }
        verify { catalog.saveProvider(any()) }
        verify { catalog.deleteProvider("ollama") }
    }

    @Test
    fun `provider profiles preserve adapter and model metadata in both directions`() {
        every { catalog.getProvider("openai") } returns
            applicationProfile.copy(
                id = "openai",
                adapter = ApplicationProviderAdapter.OPENAI_COMPATIBLE,
                models = listOf(applicationProfile.models.single().copy(status = ApplicationModelStatus.BETA)),
            )

        val profile = port.getProvider("openai")!!

        assertEquals(ProviderAdapter.OPENAI_COMPATIBLE, profile.adapter)
        assertEquals(ModelStatus.BETA, profile.models.single().status)
        assertEquals(
            mapOf("fast" to mapOf("temperature" to "0.2")),
            profile.models.single().variants,
        )
    }

    @Test
    fun `refresh and model details delegate to provider and map responses`() =
        runTest {
            coEvery { provider.getModels("ollama") } returns listOf("llama3")
            every { catalog.selectableModels("ollama") } returns applicationProfile.models
            coEvery { provider.getModelDetails("ollama", "llama3") } returns
                ShowResponse(
                    model = "llama3",
                    modifiedAt = "today",
                    details =
                        ModelDetails(
                            family = "llama",
                            parameterSize = "8B",
                            format = "gguf",
                            quantizationLevel = "Q4_K_M",
                        ),
                )

            assertEquals("llama3", port.refreshModels("ollama").single().id)
            val details = port.modelDetails("ollama", "llama3")

            assertEquals("llama3", details.model)
            assertEquals("8B", details.parameterSize)
            assertEquals("Q4_K_M", details.quantizationLevel)
            coVerify { provider.getModels("ollama") }
            coVerify { provider.getModelDetails("ollama", "llama3") }
            verify { catalog.updateDiscoveredModels("ollama", listOf("llama3")) }
        }

    @Test
    fun `staged model discovery does not update the catalog`() =
        runTest {
            val staged =
                ProviderProfile(
                    id = "staged",
                    name = "Staged OpenAI",
                    adapter = ProviderAdapter.OPENAI_COMPATIBLE,
                    baseUrl = "https://staged.example.test",
                    apiKey = "not-persisted",
                )
            coEvery { provider.getModels(any<ApplicationProviderProfile>()) } returns listOf("gpt-staged")

            assertEquals("gpt-staged", port.discoverModels(staged).single().id)

            coVerify {
                provider.getModels(
                    match<ApplicationProviderProfile> {
                        it.id == "staged" && it.baseUrl == "https://staged.example.test" && it.apiKey == "not-persisted"
                    },
                )
            }
            verify(exactly = 0) { catalog.updateDiscoveredModels(any(), any()) }
            verify(exactly = 0) { catalog.updateDiscoveredModelConfigs(any(), any()) }
        }

    @Test
    fun `codex adapter is mapped without exposing application types`() {
        every { catalog.getProvider("codex") } returns
            applicationProfile.copy(
                id = "codex",
                adapter = ApplicationProviderAdapter.CODEX_CLI,
            )

        assertEquals(ProviderAdapter.CODEX_CLI, port.getProvider("codex")?.adapter)
    }

    @Test
    fun `provider change listeners are delegated to the catalog`() {
        val handle = AutoCloseable { }
        every { catalog.addChangeListener(any()) } returns handle

        assertEquals(handle, port.addChangeListener { })
        verify { catalog.addChangeListener(any()) }
    }
}
