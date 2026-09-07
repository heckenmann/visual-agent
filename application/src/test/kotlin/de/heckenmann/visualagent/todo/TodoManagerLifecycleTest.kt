package de.heckenmann.visualagent.todo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodoManagerLifecycleTest {
    private val manager = TodoManager(InMemoryTodoStore(), TodoEventBus())

    @Test
    fun `full lifecycle - add, assign, complete`() {
        val todo = manager.add("Full lifecycle task")
        assertEquals(TodoStatus.PENDING, todo.status)

        manager.assignToAgent(todo.id, "agent-1")
        assertEquals(TodoStatus.IN_PROGRESS, todo.status)
        assertEquals("agent-1", todo.assignedAgentId)

        manager.completeTodo(todo.id)
        assertEquals(TodoStatus.COMPLETED, todo.status)
        assertNotNull(todo.completedAt)
    }

    @Test
    fun `terminal detail is visible to listeners and cleared when a todo restarts`() {
        val changes = mutableListOf<TodoChange>()
        val testManager = TodoManager(InMemoryTodoStore(), TodoEventBus())
        testManager.addListener { changes += it }
        val todo = testManager.add("Recoverable task")

        assertTrue(testManager.cancelTodo(todo.id, "Worker stopped before completion."))
        assertEquals("Worker stopped before completion.", changes.last().todo.terminalDetail)
        assertEquals("Worker stopped before completion.", changes.last().terminalDetail)

        assertTrue(testManager.updateStatus(todo.id, TodoStatus.PENDING))
        assertNull(todo.terminalDetail)
        assertNull(changes.last().todo.terminalDetail)
    }
}
