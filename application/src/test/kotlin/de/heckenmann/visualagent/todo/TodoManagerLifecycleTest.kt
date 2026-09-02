package de.heckenmann.visualagent.todo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
}
