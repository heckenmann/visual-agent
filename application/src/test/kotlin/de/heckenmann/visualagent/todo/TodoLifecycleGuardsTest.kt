package de.heckenmann.visualagent.todo

import de.heckenmann.visualagent.agent.recoverInterruptedTodos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Regression coverage for stale workers, decomposition, and interrupted execution. */
class TodoLifecycleGuardsTest {
    private val manager = TodoManager(InMemoryTodoStore(), TodoEventBus())

    @Test
    fun `repeating a status is a no-op and does not publish a transition`() {
        val changes = mutableListOf<TodoChange>()
        manager.addListener(changes::add)
        val todo = manager.add("Stable status")
        assertTrue(manager.updateStatus(todo.id, TodoStatus.PENDING))
        assertEquals(1, changes.size)
    }

    @Test
    fun `stale worker failure cannot cancel reassigned work`() {
        val todo = manager.add("Task", "first-worker")
        manager.assignToAgent(todo.id, "first-worker")
        val claimed = manager.getById(todo.id)!!
        manager.updateAssignedAgent(todo.id, "second-worker")
        assertFalse(manager.cancelTodo(todo.id, TodoTerminalReason.EXECUTION_FAILED, expected = claimed))
        assertEquals("second-worker", manager.getById(todo.id)?.assignedAgentId)
        assertEquals(TodoStatus.IN_PROGRESS, manager.getById(todo.id)?.status)
    }

    @Test
    fun `stale worker cannot complete an edited task`() {
        val todo = manager.add("Original objective")
        manager.assignToAgent(todo.id, "worker")
        val claimed = manager.getById(todo.id)!!
        manager.update(todo.id, "Changed objective")
        assertFalse(manager.completeTodo(todo.id, expected = claimed))
        assertEquals("Changed objective", manager.getById(todo.id)?.description)
        assertEquals(TodoStatus.IN_PROGRESS, manager.getById(todo.id)?.status)
    }

    @Test
    fun `stale worker cannot resurrect a deleted task`() {
        val todo = manager.add("Task")
        manager.assignToAgent(todo.id, "worker")
        val claimed = manager.getById(todo.id)!!
        manager.remove(todo.id)
        assertFalse(manager.completeTodo(todo.id, expected = claimed))
        assertTrue(manager.getAll().isEmpty())
    }

    @Test
    fun `stale decomposition cannot create children after parent cancellation`() {
        val parent = manager.add("Large task")
        manager.cancelTodo(parent.id)
        assertFalse(manager.replaceWithChildren(parent, listOf("Child task")))
        assertEquals(1, manager.getAll().size)
    }

    @Test
    fun `decomposition records executable leaf depth`() {
        val parent = manager.add("Large task")
        assertTrue(manager.replaceWithChildren(parent, listOf("First task", "Second task")))
        assertEquals(TodoStatus.CANCELLED, manager.getById(parent.id)?.status)
        assertEquals(listOf(1, 1), manager.getPending().map { it.decompositionDepth })
    }

    @Test
    fun `restart cancels interrupted work without retrying side effects`() {
        val active = manager.add("Partially executed task")
        val pending = manager.add("Not started task")
        manager.assignToAgent(active.id, "worker")
        recoverInterruptedTodos(manager)
        assertEquals(TodoStatus.CANCELLED, manager.getById(active.id)?.status)
        assertTrue(manager.getById(active.id)!!.terminalDetail!!.contains("interrupted"))
        assertEquals(TodoStatus.PENDING, manager.getById(pending.id)?.status)
        recoverInterruptedTodos(manager)
        assertEquals(TodoStatus.CANCELLED, manager.getById(active.id)?.status)
    }

    @Test
    fun `returned snapshots cannot mutate persistence`() {
        val todo = manager.add("Persisted objective")
        todo.description = "Unsaved objective"
        assertEquals("Persisted objective", manager.getById(todo.id)?.description)
    }
}
