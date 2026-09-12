package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.config.SubAgentToolConfig
import de.heckenmann.visualagent.knowledge.SubAgentConfigStore
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** Ensures removed host-filesystem tools cannot be re-enabled through configuration. */
class FileToolsTest {
    @Test
    fun `legacy host filesystem tools stay permanently unavailable`() {
        val service = AgentToolConfigService(InMemorySubAgentConfigStore())

        listOf("file:read", "file:list", "file:glob", "file:grep", "file:write", "file:edit", "terminal", "pwd").forEach {
            assertFalse(service.isToolGloballyEnabled(it))
            assertFailsWith<IllegalArgumentException> { service.setToolGloballyEnabled(it, enabled = true) }
        }
    }
}

private class InMemorySubAgentConfigStore : SubAgentConfigStore {
    private val configurations = mutableMapOf<String, SubAgentToolConfig>()

    override fun saveSubAgentConfig(config: SubAgentToolConfig) {
        configurations[config.id] = config
    }

    override fun getSubAgentConfig(id: String): SubAgentToolConfig? = configurations[id]

    override fun listSubAgentConfigs(): List<SubAgentToolConfig> = configurations.values.toList()
}
