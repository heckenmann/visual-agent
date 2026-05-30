package de.heckenmann.visualagent.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubAgentTest {
    @Test
    fun `AgentStatus enum has expected values`() {
        val statuses = AgentStatus.values()
        assertEquals(3, statuses.size)
        assertTrue(statuses.contains(AgentStatus.IDLE))
        assertTrue(statuses.contains(AgentStatus.BUSY))
        assertTrue(statuses.contains(AgentStatus.OFFLINE))
    }

    @Test
    fun `AgentStatus valueOf returns correct enum`() {
        assertEquals(AgentStatus.IDLE, AgentStatus.valueOf("IDLE"))
        assertEquals(AgentStatus.BUSY, AgentStatus.valueOf("BUSY"))
        assertEquals(AgentStatus.OFFLINE, AgentStatus.valueOf("OFFLINE"))
    }

    @Test
    fun `SubAgent default status is IDLE`() {
        val agent = SubAgent("1", "TestAgent", "Testing")
        assertEquals(AgentStatus.IDLE, agent.status)
    }

    @Test
    fun `SubAgent default currentTask is null`() {
        val agent = SubAgent("1", "TestAgent", "Testing")
        assertNull(agent.currentTask)
    }

    @Test
    fun `SubAgent default currentTodoId is null`() {
        val agent = SubAgent("1", "TestAgent", "Testing")
        assertNull(agent.currentTodoId)
    }

    @Test
    fun `SubAgent default parentAgentId is null`() {
        val agent = SubAgent("1", "TestAgent", "Testing")
        assertNull(agent.parentAgentId)
    }

    @Test
    fun `SubAgent with all properties set`() {
        val agent =
            SubAgent(
                id = "42",
                name = "Researcher",
                role = "Web research",
                status = AgentStatus.BUSY,
                currentTask = "Searching docs",
                currentTodoId = "todo-99",
                parentAgentId = "parent-1",
            )
        assertEquals("42", agent.id)
        assertEquals("Researcher", agent.name)
        assertEquals("Web research", agent.role)
        assertEquals(AgentStatus.BUSY, agent.status)
        assertEquals("Searching docs", agent.currentTask)
        assertEquals("todo-99", agent.currentTodoId)
        assertEquals("parent-1", agent.parentAgentId)
    }

    @Test
    fun `SubAgent currentTodoId is mutable`() {
        val agent = SubAgent("1", "TestAgent", "Testing")
        assertNull(agent.currentTodoId)
        agent.currentTodoId = "todo-1"
        assertEquals("todo-1", agent.currentTodoId)
        agent.currentTodoId = null
        assertNull(agent.currentTodoId)
    }

    @Test
    fun `SubAgent status is mutable`() {
        val agent = SubAgent("1", "TestAgent", "Testing")
        assertEquals(AgentStatus.IDLE, agent.status)
        agent.status = AgentStatus.BUSY
        assertEquals(AgentStatus.BUSY, agent.status)
        agent.status = AgentStatus.OFFLINE
        assertEquals(AgentStatus.OFFLINE, agent.status)
    }

    @Test
    fun `SubAgent currentTask is mutable`() {
        val agent = SubAgent("1", "TestAgent", "Testing")
        assertNull(agent.currentTask)
        agent.currentTask = "Processing data"
        assertEquals("Processing data", agent.currentTask)
        agent.currentTask = null
        assertNull(agent.currentTask)
    }

    @Test
    fun `SubAgent data class copy works`() {
        val original = SubAgent("1", "Researcher", "Web research", AgentStatus.IDLE)
        val copied = original.copy(status = AgentStatus.BUSY, currentTask = "Active task")
        assertEquals("1", copied.id)
        assertEquals("Researcher", copied.name)
        assertEquals("Web research", copied.role)
        assertEquals(AgentStatus.BUSY, copied.status)
        assertEquals("Active task", copied.currentTask)
    }

    @Test
    fun `SubAgent data class equality based on all properties`() {
        val agent1 = SubAgent("1", "Researcher", "Web research", AgentStatus.IDLE, null, null, null)
        val agent2 = SubAgent("1", "Researcher", "Web research", AgentStatus.IDLE, null, null, null)
        val agent3 = SubAgent("2", "Coder", "Code implementation", AgentStatus.BUSY)
        assertEquals(agent1, agent2)
        assertFalse(agent1 == agent3)
    }

    @Test
    fun `SubAgent same id but different currentTodoId are not equal`() {
        val agent1 = SubAgent("1", "Researcher", "Web research", AgentStatus.BUSY, currentTodoId = "todo-1")
        val agent2 = SubAgent("1", "Researcher", "Web research", AgentStatus.BUSY, currentTodoId = "todo-2")
        assertFalse(agent1 == agent2)
    }

    @Test
    fun `SubAgent with same id but different status are not equal`() {
        val agent1 = SubAgent("1", "Researcher", "Web research", AgentStatus.IDLE)
        val agent2 = SubAgent("1", "Researcher", "Web research", AgentStatus.BUSY)
        assertFalse(agent1 == agent2)
    }

    @Test
    fun `SubAgent data class toString contains properties`() {
        val agent = SubAgent("1", "Researcher", "Web research", AgentStatus.IDLE)
        val str = agent.toString()
        assertNotNull(str)
        assertTrue(str.contains("Researcher"))
        assertTrue(str.contains("IDLE"))
    }
}
