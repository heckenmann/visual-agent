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
    fun `globally disabled tools are filtered from agent tool sets`() {
        val store = MapSubAgentConfigStore()
        val service = AgentToolConfigService(store)
        val agent = SubAgent(id = "coder", name = "Coder", role = "code")

        service.setToolGloballyEnabled("file:write", false)
        service.setToolGloballyEnabled("agent:start", false)

        assertFalse(ToolId("file:write") in service.toolsFor(agent))
        assertFalse(ToolId("agent:start") in service.mainAgentTools())
        assertTrue("file:write" in service.disabledToolIds())
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
