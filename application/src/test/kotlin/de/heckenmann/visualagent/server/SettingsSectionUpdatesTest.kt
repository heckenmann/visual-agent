package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.ModelDetails
import de.heckenmann.visualagent.agent.ShowResponse
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.knowledge.MainAgentLongTermMemory
import de.heckenmann.visualagent.knowledge.MainAgentLongTermMemoryStore
import de.heckenmann.visualagent.knowledge.PreferenceStore
import de.heckenmann.visualagent.protocol.SettingsSection
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Proves the new UI destinations cannot replay stale settings into unrelated server fields. */
class SettingsSectionUpdatesTest {
    private val store = RecordingPreferences()
    private val config = AppConfigBean(store)
    private val catalog = mockk<ProviderCatalogService>(relaxed = true)
    private val model = mockk<LLMProvider>()
    private val memory =
        mockk<MainAgentLongTermMemoryStore> {
            every { snapshot() } returns MainAgentLongTermMemory("", 0, 0, java.time.Instant.EPOCH)
        }
    private val port = SpringSettingsPort(config, catalog, memory, model)

    @Test fun `appearance save writes only appearance keys and retains newer server and account settings`() {
        config.contextLength = 65536
        config.userModelInstruction = "Newer instruction"
        config.openAiApiKey = "synthetic-do-not-persist"
        config.automaticUpdatesEnabled = false
        port.saveSection(SettingsSection.APPEARANCE, SettingsSnapshot(fontSize = 18, contextLength = 4096))
        assertEquals(65536, config.contextLength)
        assertEquals("Newer instruction", config.userModelInstruction)
        assertEquals(false, config.automaticUpdatesEnabled)
        assertEquals(18, config.fontSize)
        assertEquals(setOf("ui.theme.mode", "ui.font.size", "ui.scale.percent", "ui.navigation.show.panel.labels"), store.values.keys)
        verify(exactly = 0) { catalog.setActiveSelection(any(), any()) }
    }

    @Test fun `agent and conversation sections preserve each others values`() {
        config.contextLength = 65536
        config.followUpSuggestionCount = 5
        port.saveSection(SettingsSection.AGENT, SettingsSnapshot(userModelInstruction = "Concise", timeoutSeconds = 45))
        assertEquals(5, config.followUpSuggestionCount)
        port.saveSection(SettingsSection.CONVERSATION, SettingsSnapshot(followUpSuggestionCount = 2, userModelInstruction = "Stale"))
        assertEquals("Concise", config.userModelInstruction)
        assertEquals(45, config.timeoutSeconds)
        assertEquals(2, config.followUpSuggestionCount)
        assertEquals(65536, config.contextLength)
        assertTrue(store.values.keys.none { "api.key" in it || "ui." in it })
    }

    @Test fun `model selection immediately adopts greater and smaller catalog capacities without touching other fields`() =
        runTest {
            config.timeoutSeconds = 42
            every { catalog.selectableModels("provider") } returns
                listOf(
                    ProviderModelConfig("large", contextLimit = 65536),
                    ProviderModelConfig("small", contextLimit = 8192),
                )
            port.selectConversationModel("provider", "large")
            assertEquals(65536, config.contextLength)
            port.selectConversationModel("provider", "small")
            assertEquals(8192, config.contextLength)
            assertEquals(42, config.timeoutSeconds)
            assertEquals(setOf("session.context.length"), store.values.keys)
            verify { catalog.setActiveSelection("provider", "small") }
        }

    @Test fun `missing catalog capacity is resolved through provider metadata and unknown limits are not guessed`() =
        runTest {
            every { catalog.selectableModels("provider") } returns listOf(ProviderModelConfig("reported"), ProviderModelConfig("unknown"))
            every { model.getModelDetailsReactive("provider", "reported") } returns
                Mono.just(ShowResponse("reported", "today", details = ModelDetails(contextLimit = 131072)))
            every { model.getModelDetailsReactive("provider", "unknown") } returns Mono.error(IllegalStateException("Metadata unavailable"))
            port.selectConversationModel("provider", "reported")
            assertEquals(131072, config.contextLength)
            port.selectConversationModel("provider", "unknown")
            assertEquals(131072, config.contextLength)
        }

    @Test fun `context save rejects a changed model and does not overwrite favorites`() {
        every { catalog.activeProviderId() } returns "provider"
        every { catalog.activeModelId() } returns "new"
        config.favoriteModels = "retained"
        assertFailsWith<IllegalStateException> {
            port.saveSection(SettingsSection.MODEL, SettingsSnapshot(providerId = "provider", modelId = "old", contextLength = 1000))
        }
        assertEquals("retained", config.favoriteModels)
        assertTrue(store.values.isEmpty())
    }

    @Test fun `unsupported chat cannot become active while unknown capabilities remain selectable`() =
        runTest {
            every { catalog.selectableModels("provider") } returns
                listOf(ProviderModelConfig("blocked", capabilityOverrides = mapOf("chat" to false)))
            assertFailsWith<IllegalArgumentException> { port.selectConversationModel("provider", "blocked") }
            verify(exactly = 0) { catalog.setActiveSelection(any(), any()) }
        }

    @Test fun `favorite updates write only the favorite preference`() {
        config.contextLength = 65536
        port.setModelFavorite("alpha", true)
        port.setModelFavorite("beta", true)
        port.setModelFavorite("alpha", false)
        assertEquals("beta", config.favoriteModels)
        assertEquals(65536, config.contextLength)
        assertEquals(mapOf("session.favorite.models" to "beta"), store.values)
    }
}

private class RecordingPreferences : PreferenceStore {
    val values = mutableMapOf<String, String>()

    override fun getPreference(key: String): String? = values[key]

    override fun setPreference(
        key: String,
        value: String,
    ) {
        values[key] = value
    }
}
