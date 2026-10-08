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
        assertEquals(TodoStatus.IN_PROGRESS, manager.getById(todo.id)?.status)
        assertEquals("agent-1", manager.getById(todo.id)?.assignedAgentId)

        manager.completeTodo(todo.id)
        assertEquals(TodoStatus.COMPLETED, manager.getById(todo.id)?.status)
        assertNotNull(manager.getById(todo.id)?.completedAt)
    }

    @Test
    fun `terminal detail is visible to listeners and cleared when a todo restarts`() {
        val changes = mutableListOf<TodoChange>()
        val testManager = TodoManager(InMemoryTodoStore(), TodoEventBus())
        testManager.addListener { changes += it }
        val todo = testManager.add("Recoverable task")

        assertTrue(testManager.cancelTodo(todo.id, detail = "Worker stopped before completion."))
        assertEquals("Worker stopped before completion.", changes.last().todo?.terminalDetail)
        assertEquals("Worker stopped before completion.", changes.last().terminalDetail)

        assertTrue(testManager.updateStatus(todo.id, TodoStatus.PENDING))
        assertNull(todo.terminalDetail)
        assertNull(changes.last().todo?.terminalDetail)
    }
}
