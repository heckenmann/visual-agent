package de.heckenmann.visualagent.agent
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.todo.TodoEventBus
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentManagerAutonomyOpsTest {
    @Test
    fun `seed ux todos adds predefined tasks when absent`() {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus())

        manager.seedUxTodos()

        assertTrue(manager.getTodosFromDb().isNotEmpty())
    }

    @Test
    fun `seed ux todos adds missing tasks without duplicating existing ones`() {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus(), TodoEventBus())
        manager.seedUxTodos()
        val firstCount = manager.getTodosFromDb().size
        manager.todoManager.add("ChatPanel: implement message grouping visual polish")

        manager.seedUxTodos()

        assertEquals(firstCount + 1, manager.getTodosFromDb().size)
    }
}
