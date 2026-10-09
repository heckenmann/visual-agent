package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.chat
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.provider.ModelStatus
import de.heckenmann.visualagent.agent.provider.ProviderAdapter
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.provider.ProviderConfiguration
import de.heckenmann.visualagent.agent.provider.ProviderModelConfig
import de.heckenmann.visualagent.agent.provider.ProviderProfile
import de.heckenmann.visualagent.agent.tools.api.MODEL_SELECTION_TOOL_ID
import de.heckenmann.visualagent.agent.tools.api.ModelSelectionRequest
import de.heckenmann.visualagent.agent.tools.api.ModelSelectionResult
import de.heckenmann.visualagent.server.SpringProviderPort
import de.heckenmann.visualagent.testsupport.DatabaseTest
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.testsupport.TestPersistence
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DatabaseTest
class ModelSelectionPortAdapterTest {
    private lateinit var db: TestPersistence
    private lateinit var manager: AgentManager
    private lateinit var permissions: AgentToolConfigService
    private lateinit var catalog: ProviderCatalogService
    private lateinit var adapter: ModelSelectionPortAdapter
    private val provider = mockk<LLMProvider>()
    private val main = mapOf<String, Any>("agent" to "main")
    private val profile =
        ProviderProfile(
            id = "local",
            name = "Local",
            adapter = ProviderAdapter.OLLAMA,
            baseUrl = "https://example.test",
            apiKey = "test-only-secret",
            defaultModel = "old",
            options = mapOf("temperature" to "0.2"),
            modelBlacklist = setOf("blocked"),
            models =
                listOf(
                    ProviderModelConfig("old"),
                    ProviderModelConfig("new"),
                    ProviderModelConfig("blocked"),
                    ProviderModelConfig("disabled", status = ModelStatus.DISABLED),
                ),
        )

    @BeforeEach
    fun prepare() {
        db = KnowledgeDbTestFactory.create("jdbc:h2:mem:model-selection")
        catalog = ProviderCatalogService(db.preferenceStore)
        catalog.replaceConfiguration(ProviderConfiguration(listOf(profile, profile.copy(id = "off", enabled = false)), "local", "old"))
        permissions = AgentToolConfigService(db.subAgentConfigStore, db.preferenceStore)
        manager = db.createAgentManager(provider)
        adapter = ModelSelectionPortAdapter(catalog, manager, permissions, provider)
    }

    @AfterEach
    fun close() {
        manager.destroy()
        db.close()
    }

    @Test
    fun `main selection persists through canonical catalog and notifies settings without altering profiles`() {
        var notifications = 0
        val oldRequestSelection = catalog.resolve(null, null)
        val conversationProviders = SpringProviderPort(catalog, provider)
        conversationProviders.addChangeListener { notifications++ }.use {
            StepVerifier
                .create(adapter.execute(ModelSelectionRequest("set", "local", "new"), main))
                .assertNext { assertEquals("new", it.selection.modelId) }
                .verifyComplete()
        }
        assertEquals(1, notifications)
        assertEquals("old", oldRequestSelection.model.id)
        assertEquals("new", catalog.resolve(null, null).model.id)
        assertEquals("new", ProviderCatalogService(db.preferenceStore).activeModelId())
        assertEquals("new", conversationProviders.activeModelId())
        assertEquals("local", conversationProviders.activeProviderId())
        assertEquals(profile, catalog.getProvider("local"))
        assertTrue(manager.getSubAgents().isEmpty())
    }

    @ParameterizedTest
    @EnumSource(ProviderAdapter::class)
    fun `switching provider works identically for every supported adapter`(kind: ProviderAdapter) {
        val target = profile.copy(id = "target", adapter = kind)
        catalog.saveProvider(target)

        val result = adapter.execute(ModelSelectionRequest("set", "target", "new"), main).block()!!

        assertEquals("target", result.selection.providerId)
        assertEquals("new", result.selection.modelId)
        val restored = ProviderCatalogService(db.preferenceStore)
        assertEquals("target", restored.activeProviderId())
        assertEquals("new", restored.activeModelId())
        assertEquals(target, restored.getProvider("target"))
        assertEquals(profile, restored.getProvider("local"))
    }

    @Test
    fun `explicitly authorized subagent persists only its own model and uses it on next request`() =
        runTest {
            val agent = manager.createAgent("Worker", "Research")
            val other = manager.createAgent("Other", "Research")
            val config = AgentConfig(provider = "local", model = "old", temperature = 0.4, tools = listOf(MODEL_SELECTION_TOOL_ID))
            manager.updateAgent(agent.id, config = config)
            val requests = mutableListOf<ChatRequestContext>()
            every { provider.chatReactive(any<ChatRequestContext>()) } answers {
                requests += firstArg<ChatRequestContext>()
                Mono.just(ChatResponse("model", Message("assistant", "ok"), true))
            }
            agent.chat(listOf(Message("user", "before")), provider)

            adapter.execute(ModelSelectionRequest("set", modelId = "new"), mapOf("agentId" to agent.id, "agent" to "main")).block()
            agent.chat(listOf(Message("user", "after")), provider)

            assertEquals(listOf("old", "new"), requests.map { it.model })
            val expected = config.copy(model = "new")
            assertEquals(expected, agent.config)
            assertEquals(expected, Json.decodeFromString<AgentConfig>(assertNotNull(db.subAgentStore.getAgent(agent.id)).config))
            assertEquals(other.config, Json.decodeFromString<AgentConfig>(assertNotNull(db.subAgentStore.getAgent(other.id)).config))
            assertEquals("old", catalog.activeModelId())
            assertEquals(profile, catalog.getProvider("local"))
        }

    @Test
    fun `default roles require explicit permission and global disable revokes existing grants`() {
        listOf("researcher", "coder", "analyst").forEach { role ->
            val agent = manager.createAgent(role, role, role)
            assertFalse(ToolId(MODEL_SELECTION_TOOL_ID) in permissions.toolsFor(agent))
            expectFailure(ModelSelectionRequest("set", modelId = "new"), mapOf("agentId" to agent.id), SecurityException::class.java)
        }
        val granted = manager.getSubAgents().first()
        manager.updateAgent(granted.id, config = granted.config.copy(tools = listOf(MODEL_SELECTION_TOOL_ID)))
        assertTrue(ToolId(MODEL_SELECTION_TOOL_ID) in permissions.toolsFor(granted))
        permissions.setToolGloballyEnabled(MODEL_SELECTION_TOOL_ID, false)
        assertFalse(ToolId(MODEL_SELECTION_TOOL_ID) in permissions.mainAgentTools())
        expectFailure(ModelSelectionRequest("get"), main, SecurityException::class.java)
        expectFailure(ModelSelectionRequest("set", modelId = "new"), mapOf("agentId" to granted.id), SecurityException::class.java)
        assertEquals("old", catalog.activeModelId())
    }

    @Test
    fun `unknown disabled filtered models and untrusted callers do not mutate persistence`() {
        val before = db.preferenceStore.getPreference("llm.provider.catalog.v1")
        listOf("missing", "blocked", "disabled", " ").forEach { model ->
            expectFailure(ModelSelectionRequest("set", modelId = model), main, IllegalArgumentException::class.java)
        }
        listOf("missing", "off").forEach { id ->
            expectFailure(ModelSelectionRequest("set", providerId = id, modelId = "new"), main, IllegalArgumentException::class.java)
        }
        expectFailure(ModelSelectionRequest("set", modelId = "new"), emptyMap(), IllegalArgumentException::class.java)
        expectFailure(
            ModelSelectionRequest("set", modelId = "new"),
            mapOf("agentId" to "missing", "agent" to "main"),
            IllegalArgumentException::class.java,
        )
        expectFailure(ModelSelectionRequest("invalid"), main, IllegalArgumentException::class.java)
        assertEquals(before, db.preferenceStore.getPreference("llm.provider.catalog.v1"))
    }

    @Test
    fun `discovery is bounded filtered and contains no endpoint credentials`() {
        val providers = adapter.execute(ModelSelectionRequest("listProviders"), main).block()!!
        assertEquals(listOf("local"), providers.items.map { it.id })
        val page = adapter.execute(ModelSelectionRequest("listModels", offset = 1, limit = 1), main).block()!!
        assertEquals(2, page.total)
        assertEquals(listOf("new"), page.items.map { it.id })
        val empty = adapter.execute(ModelSelectionRequest("listModels", offset = 10), main).block()!!
        assertTrue(empty.items.isEmpty())
        val current = adapter.execute(ModelSelectionRequest("get"), main).block()!!
        assertEquals("old", current.selection.modelId)
        assertFalse(Json.encodeToString(ModelSelectionResult.serializer(), providers).contains("test-only-secret"))
        expectFailure(ModelSelectionRequest("listModels", limit = 51), main, IllegalArgumentException::class.java)
    }

    @Test
    fun `refresh persists API metadata without changing selection or configured options`() {
        val discovered =
            listOf(ProviderModelConfig("old"), ProviderModelConfig("fresh", capabilities = setOf("tools"), capabilitiesComplete = true))
        every { provider.getModelConfigsReactive(profile) } returns Mono.just(discovered)

        val result = adapter.execute(ModelSelectionRequest("refreshModels", limit = 1), main).block()!!

        assertEquals(2, result.total)
        assertEquals(1, result.items.size)
        assertEquals("old", catalog.activeModelId())
        val restored = ProviderCatalogService(db.preferenceStore).getProvider("local")!!
        assertEquals(discovered, restored.models)
        assertEquals(profile.copy(models = discovered), restored)
    }

    @Test
    fun `failed refresh leaves persisted catalog untouched`() {
        val before = db.preferenceStore.getPreference("llm.provider.catalog.v1")
        every { provider.getModelConfigsReactive(profile) } returns Mono.error(IllegalStateException("API unavailable"))

        expectFailure(ModelSelectionRequest("refreshModels"), main, IllegalStateException::class.java)

        assertEquals(before, db.preferenceStore.getPreference("llm.provider.catalog.v1"))
    }

    @Test
    fun `unauthorized and invalid refreshes never query API`() {
        val agent = manager.createAgent("Worker", "Research")
        expectFailure(ModelSelectionRequest("refreshModels"), mapOf("agentId" to agent.id), SecurityException::class.java)
        expectFailure(ModelSelectionRequest("refreshModels", providerId = "off"), main, IllegalArgumentException::class.java)
        expectFailure(ModelSelectionRequest("refreshModels", limit = 51), main, IllegalArgumentException::class.java)
        io.mockk.verify(exactly = 0) { provider.getModelConfigsReactive(any()) }
    }

    @Test
    fun `authorized subagent refreshes catalog without changing its model`() {
        val agent = manager.createAgent("Worker", "Research")
        val config = AgentConfig(provider = "local", model = "old", tools = listOf(MODEL_SELECTION_TOOL_ID))
        manager.updateAgent(agent.id, config = config)
        every { provider.getModelConfigsReactive(profile) } returns
            Mono.just(listOf(ProviderModelConfig("old"), ProviderModelConfig("fresh")))

        val result = adapter.execute(ModelSelectionRequest("refreshModels"), mapOf("agentId" to agent.id)).block()!!

        assertEquals(agent.id, result.selection.agentId)
        assertEquals(listOf("old", "fresh"), result.items.map { it.id })
        assertEquals(config, agent.config)
        assertEquals("old", catalog.activeModelId())
    }

    @Test
    fun `changed provider during API request prevents saving stale discovery`() {
        val updated = profile.copy(baseUrl = "https://changed.example.test")
        every { provider.getModelConfigsReactive(profile) } returns
            Mono.fromSupplier {
                catalog.saveProvider(updated)
                listOf(ProviderModelConfig("stale"))
            }

        expectFailure(ModelSelectionRequest("refreshModels"), main, IllegalStateException::class.java)

        assertEquals(updated, ProviderCatalogService(db.preferenceStore).getProvider("local"))
        assertEquals("old", catalog.activeModelId())
    }

    @Test
    fun `revoked permission during API request prevents catalog mutation`() {
        val before = db.preferenceStore.getPreference("llm.provider.catalog.v1")
        every { provider.getModelConfigsReactive(profile) } returns
            Mono.fromSupplier {
                permissions.setToolGloballyEnabled(MODEL_SELECTION_TOOL_ID, false)
                listOf(ProviderModelConfig("fresh"))
            }

        expectFailure(ModelSelectionRequest("refreshModels"), main, SecurityException::class.java)

        assertEquals(before, db.preferenceStore.getPreference("llm.provider.catalog.v1"))
    }

    private fun expectFailure(
        request: ModelSelectionRequest,
        context: Map<String, Any>,
        type: Class<out Throwable>,
    ) {
        StepVerifier.create(adapter.execute(request, context)).expectError(type).verify()
    }
}
