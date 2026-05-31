package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.tools.ToolEventBus
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals

class AgentManagerTodoPersistenceTest {
    @Test
    fun `todos survive manager restart via database persistence`() {
        val tempDb = createTempDirectory("visual-agent-agent-todo-persist-test").resolve("agent-todos.db").toString()
        val db1 =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val provider1 = mockk<LLMProvider>(relaxed = true)
        val manager1 = AgentManager(db1, provider1, AgentToolConfigService(db1), ToolEventBus())
        manager1.todoManager.add("Persist this todo")
        assertEquals(1, manager1.todoManager.getAll().size)
        db1.close()

        val db2 =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        val provider2 = mockk<LLMProvider>(relaxed = true)
        val manager2 = AgentManager(db2, provider2, AgentToolConfigService(db2), ToolEventBus())
        assertEquals(1, manager2.todoManager.getAll().size)
        assertEquals(
            "Persist this todo",
            manager2.todoManager
                .getAll()
                .first()
                .description,
        )
        db2.close()
    }
}
