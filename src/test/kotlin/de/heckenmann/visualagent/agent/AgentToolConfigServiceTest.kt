package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.config.SubAgentToolConfig
import de.heckenmann.visualagent.knowledge.SubAgentConfigStore
import org.junit.jupiter.api.Test
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
    }

    private class MapSubAgentConfigStore : SubAgentConfigStore {
        private val configs = linkedMapOf<String, SubAgentToolConfig>()

        override fun saveSubAgentConfig(config: SubAgentToolConfig) {
            configs[config.id] = config
        }

        override fun getSubAgentConfig(id: String): SubAgentToolConfig? = configs[id]

        override fun listSubAgentConfigs(): List<SubAgentToolConfig> = configs.values.toList()
    }
}
