package de.heckenmann.visualagent.todo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodoManagerTest {
    private val manager = TodoManager()

    @Test
    fun `add creates a pending todo`() {
        val todo = manager.add("Write tests", TodoPriority.HIGH)
        assertEquals("Write tests", todo.description)
        assertEquals(TodoStatus.PENDING, todo.status)
        assertEquals(TodoPriority.HIGH, todo.priority)
        assertNull(todo.assignedAgentId)
        assertNull(todo.completedAt)
    }

    @Test
    fun `add uses MEDIUM priority by default`() {
        val todo = manager.add("Default priority task")
        assertEquals(TodoPriority.MEDIUM, todo.priority)
    }

    @Test
    fun `getAll returns all todos`() {
        manager.add("Task A")
        manager.add("Task B")
        manager.add("Task C")
        assertEquals(3, manager.getAll().size)
    }

    @Test
    fun `getPending returns only pending todos`() {
        val t1 = manager.add("Pending task")
        val t2 = manager.add("Another pending")
        manager.assignToAgent(t1.id, "agent-1")
        val pending = manager.getPending()
        assertEquals(1, pending.size)
        assertEquals(t2.id, pending[0].id)
    }

    @Test
    fun `getById returns correct todo`() {
        val todo = manager.add("Find me")
        val found = manager.getById(todo.id)
        assertNotNull(found)
        assertEquals("Find me", found.description)
    }

    @Test
    fun `getById returns null for unknown id`() {
        assertNull(manager.getById("nonexistent"))
    }

    @Test
    fun `getByAgent returns todos assigned to agent`() {
        val t1 = manager.add("Agent task 1")
        val t2 = manager.add("Agent task 2")
        manager.add("Unassigned task")
        manager.assignToAgent(t1.id, "agent-1")
        manager.assignToAgent(t2.id, "agent-1")
        val agentTodos = manager.getByAgent("agent-1")
        assertEquals(2, agentTodos.size)
    }

    @Test
    fun `assignToAgent changes status to IN_PROGRESS`() {
        val todo = manager.add("Assign me")
        val result = manager.assignToAgent(todo.id, "agent-1")
        assertTrue(result)
        assertEquals(TodoStatus.IN_PROGRESS, todo.status)
        assertEquals("agent-1", todo.assignedAgentId)
    }

    @Test
    fun `assignToAgent fails for non-pending todo`() {
        val todo = manager.add("Already in progress")
        manager.assignToAgent(todo.id, "agent-1")
        val result = manager.assignToAgent(todo.id, "agent-2")
        assertFalse(result)
        assertEquals("agent-1", todo.assignedAgentId)
    }

    @Test
    fun `assignToAgent fails for unknown todo`() {
        assertFalse(manager.assignToAgent("nonexistent", "agent-1"))
    }

    @Test
    fun `completeTodo changes status to COMPLETED`() {
        val todo = manager.add("Complete me")
        manager.assignToAgent(todo.id, "agent-1")
        val result = manager.completeTodo(todo.id)
        assertTrue(result)
        assertEquals(TodoStatus.COMPLETED, todo.status)
        assertNotNull(todo.completedAt)
    }

    @Test
    fun `completeTodo fails for pending todo`() {
        val todo = manager.add("Not in progress")
        assertFalse(manager.completeTodo(todo.id))
        assertEquals(TodoStatus.PENDING, todo.status)
    }

    @Test
    fun `completeTodo fails for unknown todo`() {
        assertFalse(manager.completeTodo("nonexistent"))
    }

    @Test
    fun `cancelTodo changes status to CANCELLED`() {
        val todo = manager.add("Cancel me")
        val result = manager.cancelTodo(todo.id)
        assertTrue(result)
        assertEquals(TodoStatus.CANCELLED, todo.status)
    }

    @Test
    fun `cancelTodo fails for already completed todo`() {
        val todo = manager.add("Already done")
        manager.assignToAgent(todo.id, "agent-1")
        manager.completeTodo(todo.id)
        assertFalse(manager.cancelTodo(todo.id))
        assertEquals(TodoStatus.COMPLETED, todo.status)
    }

    @Test
    fun `cancelTodo fails for already cancelled todo`() {
        val todo = manager.add("Already cancelled")
        manager.cancelTodo(todo.id)
        assertFalse(manager.cancelTodo(todo.id))
    }

    @Test
    fun `remove deletes todo from list`() {
        val todo = manager.add("Remove me")
        assertTrue(manager.remove(todo.id))
        assertNull(manager.getById(todo.id))
    }

    @Test
    fun `remove returns false for unknown todo`() {
        assertFalse(manager.remove("nonexistent"))
    }

    @Test
    fun `clear removes all todos`() {
        manager.add("Task 1")
        manager.add("Task 2")
        manager.clear()
        assertEquals(0, manager.getAll().size)
    }

    @Test
    fun `full lifecycle - add, assign, complete`() {
        val todo = manager.add("Full lifecycle task", TodoPriority.URGENT)
        assertEquals(TodoStatus.PENDING, todo.status)

        manager.assignToAgent(todo.id, "agent-1")
        assertEquals(TodoStatus.IN_PROGRESS, todo.status)
        assertEquals("agent-1", todo.assignedAgentId)

        manager.completeTodo(todo.id)
        assertEquals(TodoStatus.COMPLETED, todo.status)
        assertNotNull(todo.completedAt)
    }
}
