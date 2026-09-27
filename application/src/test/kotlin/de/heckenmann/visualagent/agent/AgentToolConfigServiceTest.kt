package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.config.SubAgentToolConfig
import de.heckenmann.visualagent.knowledge.PreferenceStore
import de.heckenmann.visualagent.knowledge.SubAgentConfigStore
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentToolConfigServiceTest {
    @Test
    fun `main agent tool set includes sub-agent definition tools and todos`() {
        val store = MapSubAgentConfigStore()
        val service = AgentToolConfigService(store)

        val tools = service.mainAgentTools().map { it.value }.toSet()
        assertTrue("agent:list" in tools)
        assertTrue("agent:show" in tools)
        assertTrue("agent:create" in tools)
        assertTrue("agent:update" in tools)
        assertTrue("agent:delete" in tools)
        assertTrue("agent:log" in tools)
        assertTrue("todos" in tools)
        assertTrue("skills" in tools)
        assertTrue("tool:help" in tools)
        assertTrue("workspace:file" in tools)
        assertTrue("workspace:download" in tools)
        assertTrue("update:check" in tools)
        assertTrue("context" in tools)
        assertTrue("system:client-runtime" in tools)
        assertTrue("diagnostics:config" in tools)
        assertTrue("diagnostics:database" in tools)
        assertTrue("diagnostics:provider" in tools)
        assertTrue("diagnostics:health" in tools)
        assertTrue("diagnostics:connectors" in tools)
        assertFalse("agent:start" in tools)
        assertFalse("agent:message" in tools)
        assertFalse("agent:assign-todo" in tools)
        assertFalse("agent:assign-next-todo" in tools)
        assertFalse("agent:assign-all-todos" in tools)
        assertFalse("file:write" in tools)
        assertFalse("terminal" in tools)
    }

    @Test
    fun `template name is resolved from stored agent config`() {
        val store = MapSubAgentConfigStore()
        val service = AgentToolConfigService(store)
        val agent = SubAgent(id = "a", name = "Coder", role = "Implementation", config = AgentConfig.fromTemplate("coder"))

        assertTrue(service.findConfigIdFor(agent) == "coder")
        assertTrue(ToolId("workspace:file") in service.toolsFor(agent))
        assertFalse(ToolId("file:write") in service.toolsFor(agent))
    }

    @Test
    fun `server diagnostics are enabled only for researcher and analyst defaults`() {
        val service = AgentToolConfigService(MapSubAgentConfigStore())
        val researcher =
            SubAgent(id = "researcher", name = "Researcher", role = "Research", config = AgentConfig.fromTemplate("researcher"))
        val analyst = SubAgent(id = "analyst", name = "Analyst", role = "Analysis", config = AgentConfig.fromTemplate("analyst"))
        val coder = SubAgent(id = "coder", name = "Coder", role = "Implementation", config = AgentConfig.fromTemplate("coder"))
        val diagnosticTools =
            setOf(
                ToolId("system:time"),
                ToolId("network:dns"),
                ToolId("network:reverse-dns"),
                ToolId("network:tcp"),
                ToolId("network:ping"),
                ToolId("network:traceroute"),
                ToolId("network:interfaces"),
                ToolId("network:routes"),
                ToolId("network:http"),
                ToolId("network:tls"),
                ToolId("system:threads"),
                ToolId("system:gc"),
                ToolId("system:process"),
                ToolId("system:filesystem"),
            )

        assertTrue(diagnosticTools.all { it in service.toolsFor(researcher) })
        assertTrue(diagnosticTools.all { it in service.toolsFor(analyst) })
        assertTrue(diagnosticTools.none { it in service.toolsFor(coder) })
        val processInventory = ToolId("system:processes")
        assertFalse(processInventory in service.toolsFor(researcher))
        assertFalse(processInventory in service.toolsFor(analyst))
        val clientProcessInventory = ToolId("system:client-processes")
        assertFalse(clientProcessInventory in service.toolsFor(researcher))
        assertFalse(clientProcessInventory in service.toolsFor(analyst))
        assertFalse(processInventory in service.mainAgentTools())
        assertFalse(clientProcessInventory in service.mainAgentTools())
        service.setToolGloballyEnabled(processInventory.value, true)
        service.setToolGloballyEnabled(clientProcessInventory.value, true)
        assertTrue(processInventory in service.mainAgentTools())
        assertTrue(clientProcessInventory in service.mainAgentTools())
        val explicitlyEnabled =
            SubAgent(id = "custom", name = "Custom", role = "Custom", config = AgentConfig(tools = listOf(processInventory.value)))
        assertTrue(processInventory in service.toolsFor(explicitlyEnabled))
        val clientRuntime = ToolId("system:client-runtime")
        val clientToolsRequested =
            SubAgent(
                id = "client-custom",
                name = "Client Custom",
                role = "Custom",
                config = AgentConfig(tools = listOf(clientProcessInventory.value, clientRuntime.value)),
            )
        assertTrue(clientRuntime in service.mainAgentTools())
        assertFalse(clientProcessInventory in service.toolsFor(clientToolsRequested))
        assertFalse(clientRuntime in service.toolsFor(clientToolsRequested))
    }

    @Test
    fun `log diagnostics are main-agent-only and disabled until explicitly enabled`() {
        val store = MapSubAgentConfigStore()
        val service = AgentToolConfigService(store)
        val researcher = SubAgent(id = "r", name = "Researcher", role = "Research", config = AgentConfig.fromTemplate("researcher"))

        assertFalse(ToolId("diagnostics:logs") in service.mainAgentTools())
        assertFalse(ToolId("diagnostics:logs") in service.toolsFor(researcher))

        service.setToolGloballyEnabled("diagnostics:logs", true)

        assertTrue(ToolId("diagnostics:logs") in service.mainAgentTools())
        assertFalse(ToolId("diagnostics:logs") in service.toolsFor(researcher))
    }

    @Test
    fun `TLS management tools are main-agent-only and disabled by default`() {
        val store = MapSubAgentConfigStore()
        val service = AgentToolConfigService(store)
        val trustStoreTool = ToolId("security:truststore")
        val keyStoreTool = ToolId("security:keystore")
        val agent =
            SubAgent(
                id = "custom",
                name = "Custom",
                role = "Custom",
                config = AgentConfig(tools = listOf(trustStoreTool.value, keyStoreTool.value)),
            )

        assertTrue(trustStoreTool !in service.mainAgentTools())
        assertTrue(keyStoreTool !in service.mainAgentTools())
        assertTrue(trustStoreTool !in service.toolsFor(agent))
        assertTrue(keyStoreTool !in service.toolsFor(agent))

        service.setToolGloballyEnabled(trustStoreTool.value, true)
        service.setToolGloballyEnabled(keyStoreTool.value, true)

        assertTrue(trustStoreTool in service.mainAgentTools())
        assertTrue(keyStoreTool in service.mainAgentTools())
        assertTrue(trustStoreTool !in service.toolsFor(agent))
        assertTrue(keyStoreTool !in service.toolsFor(agent))
        AgentToolConfigService(store)
        assertTrue(trustStoreTool in service.mainAgentTools())
        assertTrue(keyStoreTool in service.mainAgentTools())
    }

    @Test
    fun `configuration diagnostics are main-agent-only`() {
        val service = AgentToolConfigService(MapSubAgentConfigStore())
        val researcher =
            SubAgent(id = "researcher", name = "Researcher", role = "Research", config = AgentConfig.fromTemplate("researcher"))
        val explicit =
            SubAgent(
                id = "custom",
                name = "Custom",
                role = "Custom",
                config =
                    AgentConfig(
                        tools = listOf("diagnostics:config", "diagnostics:provider", "diagnostics:health", "diagnostics:connectors"),
                    ),
            )

        assertTrue(ToolId("diagnostics:config") in service.mainAgentTools())
        assertTrue(ToolId("diagnostics:provider") in service.mainAgentTools())
        assertFalse(ToolId("diagnostics:config") in service.toolsFor(researcher))
        assertFalse(ToolId("diagnostics:config") in service.toolsFor(explicit))
        assertFalse(ToolId("diagnostics:provider") in service.toolsFor(explicit))
        assertFalse(ToolId("diagnostics:health") in service.toolsFor(explicit))
        assertFalse(ToolId("diagnostics:connectors") in service.toolsFor(explicit))
    }

    @Test
    fun `server environment access is globally disabled and requires explicit enablement`() {
        val store = MapSubAgentConfigStore()
        val service = AgentToolConfigService(store)
        val tool = ToolId("system:env")
        val explicitAgent =
            SubAgent(
                id = "custom",
                name = "Custom",
                role = "Custom",
                config = AgentConfig(tools = listOf(tool.value)),
            )

        assertFalse(tool in service.mainAgentTools())
        assertFalse(tool in service.toolsFor(explicitAgent))

        service.setToolGloballyEnabled(tool.value, true)

        assertTrue(tool in service.mainAgentTools())
        assertTrue(tool in service.toolsFor(explicitAgent))
        val defaultResearcher =
            SubAgent(id = "researcher", name = "Researcher", role = "Research", config = AgentConfig.fromTemplate("researcher"))
        assertFalse(tool in service.toolsFor(defaultResearcher))
    }

    @Test
    fun `template name falls back to researcher when no config stored and no tools match`() {
        val store = MapSubAgentConfigStore()
        val service = AgentToolConfigService(store)
        val agent = SubAgent(id = "a", name = "CanvasPainter", role = "Painting", config = AgentConfig())

        assertTrue(service.findConfigIdFor(agent) == null)
        assertTrue(ToolId("workspace:file") in service.toolsFor(agent))
        assertFalse(ToolId("file:read") in service.toolsFor(agent))
    }

    @Test
    fun `existing default configs receive newly introduced default tools`() {
        val store = MapSubAgentConfigStore()
        store.saveSubAgentConfig(
            SubAgentToolConfig(
                id = "coder",
                name = "Coder",
                description = "Existing config",
                tools = listOf("file:read"),
            ),
        )

        AgentToolConfigService(store)

        val updated = store.getSubAgentConfig("coder")!!
        assertTrue("file:read" in updated.tools)
        assertTrue("workspace:layout" in updated.tools)
        assertTrue("canvas" in updated.tools)
        assertTrue("usecases" in updated.tools)
    }

    @Test
    fun `per agent tool overrides are resolved before template defaults`() {
        val store = MapSubAgentConfigStore()
        val service = AgentToolConfigService(store)
        val agent =
            SubAgent(
                id = "coder",
                name = "Coder",
                role = "code",
                config = AgentConfig(tools = listOf("canvas", "file:write")),
            )

        service.setToolGloballyEnabled("file:write", false)

        assertTrue(ToolId("canvas") in service.toolsFor(agent))
        assertFalse(ToolId("file:read") in service.toolsFor(agent))
        assertFalse(ToolId("file:write") in service.toolsFor(agent))
    }

    @Test
    fun `globally disabled tool help is excluded for main and sub-agents`() {
        val store = MapSubAgentConfigStore()
        val service = AgentToolConfigService(store)
        val agent = SubAgent(id = "a", name = "Coder", role = "Implementation", config = AgentConfig.fromTemplate("coder"))

        service.setToolGloballyEnabled("tool:help", false)

        assertFalse(ToolId("tool:help") in service.mainAgentTools())
        assertFalse(ToolId("tool:help") in service.toolsFor(agent))
    }

    private class MapSubAgentConfigStore :
        SubAgentConfigStore,
        PreferenceStore {
        private val configs = linkedMapOf<String, SubAgentToolConfig>()
        private val preferences = linkedMapOf<String, String>()

        override fun saveSubAgentConfig(config: SubAgentToolConfig) {
            configs[config.id] = config
        }

        override fun getSubAgentConfig(id: String): SubAgentToolConfig? = configs[id]

        override fun listSubAgentConfigs(): List<SubAgentToolConfig> = configs.values.toList()

        override fun getPreference(key: String): String? = preferences[key]

        override fun setPreference(
            key: String,
            value: String,
        ) {
            preferences[key] = value
        }
    }
}
