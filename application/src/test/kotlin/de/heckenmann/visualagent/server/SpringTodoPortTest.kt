package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.protocol.TodoState
import de.heckenmann.visualagent.protocol.TodoUpdate
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoChange
import de.heckenmann.visualagent.todo.TodoChangeType
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoProgressUpdate
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.todo.TodoUpdateCommand
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies todo mapping and event forwarding at the Spring-to-protocol seam. */
class SpringTodoPortTest {
    private val manager = mockk<AgentManager>()
    private val todoManager = mockk<TodoManager>(relaxed = true)
    private val eventBus = TodoEventBus()
    private val port = SpringTodoPort(manager, eventBus)

    @Test
    fun `list and agents map application models to protocol models`() {
        val todo = Todo("todo-1", "Ship it", TodoStatus.COMPLETED, position = 2)
        val agent = SubAgent("agent-1", "Researcher", "research", AgentStatus.IDLE)
        every { manager.getTodosFromDb() } returns listOf(todo)
        every { manager.getSubAgents() } returns listOf(agent)

        assertEquals(TodoState.COMPLETED, port.list().single().status)
        assertEquals("Researcher", port.agents().single().name)
    }

    @Test
    fun `mutations delegate through the todo manager`() {
        every { manager.todoManager } returns todoManager
        every { todoManager.update("todo-1", "Updated") } returns true
        every { todoManager.update(any<TodoUpdateCommand>()) } returns true
        every { todoManager.updateStatus("todo-1", TodoStatus.IN_PROGRESS) } returns true
        every { todoManager.updateAssignedAgent("todo-1", "agent-1") } returns true
        every { todoManager.reorder(listOf("todo-1")) } returns true
        every { todoManager.remove("todo-1") } returns true

        assertTrue(port.updateDescription("todo-1", "Updated"))
        assertTrue(port.update(TodoUpdate("todo-1", "Updated", TodoState.IN_PROGRESS, "agent-1")))
        assertTrue(port.updateStatus("todo-1", TodoState.IN_PROGRESS))
        assertTrue(port.updateAssignedAgent("todo-1", "agent-1"))
        assertTrue(port.reorder(listOf("todo-1")))
        assertTrue(port.remove("todo-1"))

        verify { todoManager.update("todo-1", "Updated") }
        verify {
            todoManager.update(
                TodoUpdateCommand(
                    id = "todo-1",
                    description = "Updated",
                    assignment =
                        de.heckenmann.visualagent.todo.TodoAssignmentChange
                            .Set("agent-1"),
                    status = TodoStatus.IN_PROGRESS,
                ),
            )
        }
        verify { todoManager.updateStatus("todo-1", TodoStatus.IN_PROGRESS) }
        verify { todoManager.updateAssignedAgent("todo-1", "agent-1") }
        verify { todoManager.reorder(listOf("todo-1")) }
        verify { todoManager.remove("todo-1") }
    }

    @Test
    fun `todo changes and progress are forwarded and removable`() {
        val changes = mutableListOf<String?>()
        val progress = mutableListOf<String>()
        val changeHandle = port.addListener { changes += it.todoId }
        val progressHandle = port.addProgressListener { progress += it.delta }

        eventBus.publish(TodoChange(TodoChangeType.REMOVED, todoId = "todo-2"))
        eventBus.publishProgress(TodoProgressUpdate("todo-1", delta = "chunk"))

        assertEquals(listOf<String?>("todo-2"), changes)
        assertEquals(listOf<String>("chunk"), progress)

        changeHandle.close()
        progressHandle.close()
        eventBus.publish(TodoChange(TodoChangeType.REMOVED, todoId = "todo-3"))
        eventBus.publishProgress(TodoProgressUpdate("todo-1", delta = "ignored"))
        assertEquals(listOf<String?>("todo-2"), changes)
        assertEquals(listOf<String>("chunk"), progress)
    }
}
