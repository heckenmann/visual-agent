package de.heckenmann.visualagent.todo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TodoEventBusTest {
    @Test
    fun `publish sends event to all listeners`() {
        val bus = TodoEventBus()
        val received = mutableListOf<TodoChange>()
        val todo = Todo(id = "t1", description = "Task")
        bus.addListener { received += it }

        bus.publish(TodoChange(TodoChangeType.ADDED, todo = todo))

        assertEquals(1, received.size)
        assertEquals(TodoChangeType.ADDED, received.first().type)
        assertEquals("t1", received.first().todo?.id)
    }

    @Test
    fun `removed listener does not receive events`() {
        val bus = TodoEventBus()
        val received = mutableListOf<TodoChange>()
        val handle = bus.addListener { received += it }
        handle.close()

        bus.publish(TodoChange(TodoChangeType.ADDED, todo = Todo(id = "t1", description = "Task")))

        assertTrue(received.isEmpty())
    }
}
