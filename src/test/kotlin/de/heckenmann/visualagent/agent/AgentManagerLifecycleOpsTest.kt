package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.todo.TodoPriority
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentManagerLifecycleOpsTest {
    @Test
    fun `getTodosFromDb returns stored todos`() {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())
        manager.todoManager.add("first", TodoPriority.MEDIUM)
        manager.todoManager.add("second", TodoPriority.HIGH)

        val todos = manager.getTodosFromDb()

        assertEquals(2, todos.size)
    }

    @Test
    fun `getTodoSummaryFromDb returns correct counters`() {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())
        manager.todoManager.add("one", TodoPriority.MEDIUM)
        manager.todoManager.add("two", TodoPriority.HIGH)
        val todos = manager.todoManager.getAll()
        manager.todoManager.updateStatus(todos[0].id, de.heckenmann.visualagent.todo.TodoStatus.IN_PROGRESS)
        manager.todoManager.updateStatus(todos[1].id, de.heckenmann.visualagent.todo.TodoStatus.COMPLETED)

        val summary = manager.getTodoSummaryFromDb()

        assertEquals(2, summary.total)
        assertEquals(0, summary.open)
        assertEquals(1, summary.inProgress)
        assertEquals(1, summary.completed)
    }

    @Test
    fun `update agent modifies name role and config`() {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())
        val agent = manager.createAgent("Worker", "Worker role")
        val config = AgentConfig(timeout = 999)

        val updated = manager.updateAgent(agent.id, "Renamed", "Updated role", config)

        assertTrue(updated)
        assertEquals("Renamed", agent.name)
        assertEquals("Updated role", agent.role)
        assertEquals(999, agent.config.timeout)
        val fromDb = manager.getSubAgentsFromDb().first { it.id == agent.id }
        assertEquals("Renamed", fromDb.name)
    }

    @Test
    fun `update unknown agent returns false`() {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())

        assertFalse(manager.updateAgent("missing", "Name", "Role"))
    }

    @Test
    fun `delete agent removes from memory and persistence`() {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())
        val agent = manager.createAgent("Worker", "Worker role")

        val deleted = manager.deleteAgent(agent.id)

        assertTrue(deleted)
        assertNull(manager.getSubAgent(agent.id))
        assertTrue(manager.getSubAgentsFromDb().none { it.id == agent.id })
    }

    @Test
    fun `delete unknown agent returns false`() {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())

        assertFalse(manager.deleteAgent("missing"))
    }

    @Test
    fun `getSubAgentsFromDb loads persisted agents`() {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())
        val agent = manager.createAgent("Worker", "Worker role")

        val fromDb = manager.getSubAgentsFromDb()

        assertTrue(fromDb.any { it.id == agent.id })
    }

    @Test
    fun `lifecycle ops maps corrupted config to default`() {
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())
        val agent = manager.createAgent("Worker", "Worker role")
        db.saveAgent(
            de.heckenmann.visualagent.knowledge.PersistedSubAgent(
                id = agent.id,
                name = agent.name,
                role = agent.role,
                status = "IDLE",
                currentTask = null,
                parentAgentId = null,
                config = "not valid json",
                createdAt = java.time.Instant.now(),
                updatedAt = java.time.Instant.now(),
            ),
        )

        val fromDb = manager.getSubAgentsFromDb().first { it.id == agent.id }

        assertNotNull(fromDb)
        assertEquals(AgentConfig(), fromDb.config)
    }
}
