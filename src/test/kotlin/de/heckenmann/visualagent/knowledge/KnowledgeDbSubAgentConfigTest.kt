package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.AgentToolConfigService
import de.heckenmann.visualagent.agent.SubAgentToolConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KnowledgeDbSubAgentConfigTest {
    @Test
    fun `sub agent config crud persists tool ids`() {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create("jdbc:sqlite::memory:")
        val service = AgentToolConfigService(db)
        val config =
            SubAgentToolConfig(
                id = "custom",
                name = "Custom",
                description = "Custom config",
                model = "test-model",
                systemPrompt = "Be precise.",
                tools = listOf("file:read", "terminal"),
                maxTurns = 3,
                enabled = true,
            )

        service.save(config)

        val loaded = db.getSubAgentConfig("custom")
        assertNotNull(loaded)
        assertEquals(listOf("file:read", "terminal"), loaded.tools)
        assertEquals("test-model", loaded.model)
        assertTrue(db.listSubAgentConfigs().any { it.id == "custom" })
    }
}
