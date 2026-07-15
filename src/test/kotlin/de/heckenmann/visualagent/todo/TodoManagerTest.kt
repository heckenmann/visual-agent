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
    fun `add persists new todo to store when store is provided`() {
        val saved = mutableListOf<Todo>()
        val store =
            object : de.heckenmann.visualagent.knowledge.TodoStore {
                override fun saveTodo(todo: Todo) {
                    saved += todo
                }

                override fun listTodos(): List<Todo> = saved

                override fun deleteTodo(todoId: String) {
                    saved.removeIf { it.id == todoId }
                }

                override fun clearTodos() {
                    saved.clear()
                }
            }
        val managerWithStore = TodoManager(todoStore = store)

        val todo = managerWithStore.add("Persist me")

        assertEquals(1, saved.size)
        assertEquals(todo.id, saved[0].id)
        assertEquals("Persist me", saved[0].description)
    }

    @Test
    fun `add creates a pending todo`() {
        val todo = manager.add("Write tests")
        assertEquals("Write tests", todo.description)
        assertEquals(TodoStatus.PENDING, todo.status)
        assertEquals(0, todo.position)
        assertNull(todo.assignedAgentId)
        assertNull(todo.completedAt)
    }

    @Test
    fun `add appends todos at increasing positions`() {
        val first = manager.add("First")
        val second = manager.add("Second")
        assertEquals(0, first.position)
        assertEquals(1, second.position)
    }

    @Test
    fun `getAll returns all todos ordered by position`() {
        val a = manager.add("A")
        val b = manager.add("B")
        val c = manager.add("C")
        manager.moveToPosition(c.id, 0)
        assertEquals(listOf(c, a, b), manager.getAll())
    }

    @Test
    fun `getPending returns only pending todos ordered by position`() {
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
    fun `updateStatus publishes update and manages completed timestamp`() {
        val changes = mutableListOf<TodoChange>()
        val manager = TodoManager(onChange = changes::add)
        val todo = manager.add("Status task")

        assertTrue(manager.updateStatus(todo.id, TodoStatus.COMPLETED))
        assertEquals(TodoStatus.COMPLETED, todo.status)
        assertNotNull(todo.completedAt)
        assertEquals(TodoChangeType.UPDATED, changes.last().type)

        assertTrue(manager.updateStatus(todo.id, TodoStatus.PENDING))
        assertEquals(TodoStatus.PENDING, todo.status)
        assertNull(todo.completedAt)
        assertEquals(TodoChangeType.UPDATED, changes.last().type)
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
    fun `moveToPosition reorders todos`() {
        val a = manager.add("A")
        val b = manager.add("B")
        val c = manager.add("C")
        assertTrue(manager.moveToPosition(c.id, 0))
        assertEquals(listOf(c, a, b), manager.getAll())
        assertEquals(0, c.position)
        assertEquals(1, a.position)
        assertEquals(2, b.position)
    }

    @Test
    fun `moveToPosition coerces target into bounds`() {
        val a = manager.add("A")
        manager.add("B")
        assertTrue(manager.moveToPosition(a.id, 99))
        assertEquals(1, a.position)
    }

    @Test
    fun `moveToPosition returns false for unknown todo`() {
        assertFalse(manager.moveToPosition("nonexistent", 0))
    }

    @Test
    fun `reorder reorders by ids`() {
        val a = manager.add("A")
        val b = manager.add("B")
        val c = manager.add("C")
        assertTrue(manager.reorder(listOf(c.id, a.id, b.id)))
        assertEquals(listOf(c, a, b), manager.getAll())
    }

    @Test
    fun `reorder returns false when ids do not match`() {
        manager.add("A")
        assertFalse(manager.reorder(listOf("nonexistent")))
    }

    @Test
    fun `update changes description`() {
        val todo = manager.add("Original")
        assertTrue(manager.update(todo.id, "Updated"))
        assertEquals("Updated", manager.getById(todo.id)?.description)
    }

    @Test
    fun `update returns false for unknown todo`() {
        assertFalse(manager.update("nonexistent", "Updated"))
    }

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
