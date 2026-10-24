package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.MainAgentLongTermMemory
import de.heckenmann.visualagent.knowledge.MainAgentLongTermMemoryStore
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** Verifies independent assignment and validation at the settings boundary. */
class PromptInterpolationSettingsTest {
    private val config = AppConfigBean()
    private val catalog = mockk<ProviderCatalogService>(relaxed = true)
    private val memory =
        mockk<MainAgentLongTermMemoryStore>().also {
            every { it.snapshot() } returns
                MainAgentLongTermMemory("", 0, 0, java.time.Instant.EPOCH)
        }
    private val port = SpringSettingsPort(config, catalog, memory, mockk(relaxed = true))

    @Test
    fun `assignment round trips independently of the active model`() {
        every { catalog.listProviders() } returns
            listOf(ProviderProfile("other", "Other", ProviderAdapter.CODEX_CLI, "", models = listOf(ProviderModelConfig("rewrite"))))
        port.save(
            SettingsSnapshot(
                automaticPromptInterpolation = true,
                promptInterpolationContextMessages = 5,
                promptInterpolationProviderId = "other",
                promptInterpolationModelId = "rewrite",
            ),
        )
        val restored = port.snapshot()
        assertEquals(true, restored.automaticPromptInterpolation)
        assertEquals(5, restored.promptInterpolationContextMessages)
        assertEquals("other", restored.promptInterpolationProviderId)
        assertEquals("rewrite", restored.promptInterpolationModelId)
        verify(exactly = 0) { catalog.setActiveSelection(any(), any()) }
    }

    @Test
    fun `explicit non chat interpolation model is rejected without changing configuration`() {
        every { catalog.listProviders() } returns
            listOf(
                ProviderProfile(
                    "other",
                    "Other",
                    ProviderAdapter.CODEX_CLI,
                    "",
                    models =
                        listOf(
                            ProviderModelConfig(
                                "rewrite",
                                capabilityOverrides =
                                    mapOf(
                                        "chat" to false,
                                    ),
                            ),
                        ),
                ),
            )
        assertFailsWith<IllegalArgumentException> {
            port.save(
                SettingsSnapshot(
                    automaticPromptInterpolation = true,
                    promptInterpolationProviderId = "other",
                    promptInterpolationModelId = "rewrite",
                ),
            )
        }
        assertFalse(config.automaticPromptInterpolation)
        verify(exactly = 0) { catalog.replaceConfiguration(any()) }
    }

    @Test
    fun `invalid settings fail before configuration mutations`() {
        for (settings in listOf(
            SettingsSnapshot(promptInterpolationContextMessages = 4),
            SettingsSnapshot(promptInterpolationContextMessages = 11),
            SettingsSnapshot(promptInterpolationProviderId = "other"),
        )) {
            assertFailsWith<IllegalArgumentException> { port.save(settings.copy(automaticPromptInterpolation = true)) }
            assertFalse(config.automaticPromptInterpolation)
        }
    }
}
