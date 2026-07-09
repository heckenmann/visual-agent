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
    fun `main agent tool set only includes sub-agent definition tools`() {
        val store = MapSubAgentConfigStore()
        val service = AgentToolConfigService(store)

        val tools = service.mainAgentTools().map { it.value }.toSet()
        assertTrue("agent:list" in tools)
        assertTrue("agent:create" in tools)
        assertTrue("agent:update" in tools)
        assertTrue("agent:delete" in tools)
        assertFalse("agent:start" in tools)
        assertFalse("agent:message" in tools)
        assertFalse("agent:assign-todo" in tools)
        assertFalse("agent:assign-next-todo" in tools)
        assertFalse("agent:assign-all-todos" in tools)
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
