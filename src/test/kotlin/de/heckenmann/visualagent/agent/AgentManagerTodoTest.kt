package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.knowledge.KnowledgeDb
import de.heckenmann.visualagent.todo.TodoPriority
import de.heckenmann.visualagent.todo.TodoStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentManagerTodoTest {

    private fun createManager(): Triple<AgentManager, LLMProvider, KnowledgeDb> {
        val db = KnowledgeDb("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        coEvery { provider.isConnected() } returns true
        coEvery { provider.chat(any()) } returns ChatResponse(
            model = "test",
            message = Message("assistant", "Task completed"),
            done = true,
        )
        val manager = AgentManager(db, provider)
        return Triple(manager, provider, db)
    }

    @Test
    fun `assignNextTodo assigns pending todo to idle agent`() {
        val (manager, _, _) = createManager()
        val todo = manager.todoManager.add("Research topic X", TodoPriority.HIGH)

        val result = manager.assignNextTodo()

        assertTrue(result)
        val agent = manager.getSubAgents().first { it.status == AgentStatus.BUSY }
        assertEquals(todo.id, agent.currentTodoId)
        assertEquals("Research topic X", agent.currentTask)
    }

    @Test
    fun `assignNextTodo returns false when no pending todos`() {
        val (manager, _, _) = createManager()

        val result = manager.assignNextTodo()

        assertFalse(result)
    }

    @Test
    fun `assignNextTodo returns false when no idle agents`() {
        val (manager, _, _) = createManager()
        manager.getSubAgents().forEach { it.status = AgentStatus.BUSY }
        manager.todoManager.add("Orphan task")

        val result = manager.assignNextTodo()

        assertFalse(result)
    }

    @Test
    fun `assignTodoToAgent assigns specific todo to specific agent`() {
        val (manager, _, _) = createManager()
        val todo = manager.todoManager.add("Code review")
        val agentId = "1"

        val result = manager.assignTodoToAgent(todo.id, agentId)

        assertTrue(result)
        val agent = manager.getSubAgent(agentId)!!
        assertEquals(AgentStatus.BUSY, agent.status)
        assertEquals(todo.id, agent.currentTodoId)
        assertEquals(TodoStatus.IN_PROGRESS, manager.todoManager.getById(todo.id)!!.status)
    }

    @Test
    fun `assignTodoToAgent fails for busy agent`() {
        val (manager, _, _) = createManager()
        val t1 = manager.todoManager.add("First task")
        val t2 = manager.todoManager.add("Second task")
        manager.assignTodoToAgent(t1.id, "1")

        val result = manager.assignTodoToAgent(t2.id, "1")

        assertFalse(result)
    }

    @Test
    fun `assignTodoToAgent fails for unknown agent`() {
        val (manager, _, _) = createManager()
        val todo = manager.todoManager.add("Mystery task")

        assertFalse(manager.assignTodoToAgent(todo.id, "nonexistent"))
    }

    @Test
    fun `assignTodoToAgent fails for unknown todo`() {
        val (manager, _, _) = createManager()

        assertFalse(manager.assignTodoToAgent("nonexistent", "1"))
    }

    @Test
    fun `assignAllPendingTodos assigns multiple todos to multiple agents`() {
        val (manager, _, _) = createManager()
        manager.todoManager.add("Task 1")
        manager.todoManager.add("Task 2")
        manager.todoManager.add("Task 3")

        val count = manager.assignAllPendingTodos()

        assertEquals(3, count)
        val assignedTodos = manager.todoManager.getAll().filter { it.assignedAgentId != null }
        assertEquals(3, assignedTodos.size)
    }

    @Test
    fun `assignAllPendingTodos stops when agents are exhausted`() {
        val (manager, _, _) = createManager()
        repeat(10) { manager.todoManager.add("Task $it") }

        val count = manager.assignAllPendingTodos()

        assertEquals(3, count)
        val busyAgents = manager.getSubAgents().filter { it.status == AgentStatus.BUSY }
        assertEquals(3, busyAgents.size)
    }

    @Test
    fun `todo status transitions correctly on assign`() {
        val (manager, _, _) = createManager()
        val todo = manager.todoManager.add("Verify status")

        manager.assignNextTodo()

        assertEquals(TodoStatus.IN_PROGRESS, manager.todoManager.getById(todo.id)!!.status)
        assertNotNull(manager.todoManager.getById(todo.id)!!.assignedAgentId)
    }

    @Test
    fun `only one agent gets busy per assignNextTodo call`() {
        val (manager, _, _) = createManager()
        manager.todoManager.add("Solo task")

        manager.assignNextTodo()

        val busyCount = manager.getSubAgents().count { it.status == AgentStatus.BUSY }
        assertEquals(1, busyCount)
    }

    @Test
    fun `agent currentTodoId is null when idle`() {
        val (manager, _, _) = createManager()

        manager.getSubAgents().forEach { agent ->
            assertNull(agent.currentTodoId)
            assertEquals(AgentStatus.IDLE, agent.status)
            assertNull(agent.currentTask)
        }
    }

    @Test
    fun `agents have expected default roles`() {
        val (manager, _, _) = createManager()
        val agents = manager.getSubAgents()

        assertEquals(3, agents.size)
        // Agents are now loaded from DB and may not be in insertion order
        val agentsByName = agents.associateBy { it.name }
        assertTrue(agentsByName.containsKey("Researcher"))
        assertTrue(agentsByName.containsKey("Coder"))
        assertTrue(agentsByName.containsKey("Documenter"))
    }

    @Test
    fun `assignNextTodo picks first idle agent and first pending todo`() {
        val (manager, _, _) = createManager()
        val highPrio = manager.todoManager.add("Urgent task", TodoPriority.URGENT)
        val lowPrio = manager.todoManager.add("Low task", TodoPriority.LOW)

        manager.assignNextTodo()

        val busyAgent = manager.getSubAgents().first { it.status == AgentStatus.BUSY }
        assertEquals(highPrio.id, busyAgent.currentTodoId)
    }
}
