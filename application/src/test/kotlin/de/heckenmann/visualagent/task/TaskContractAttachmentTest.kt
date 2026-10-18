package de.heckenmann.visualagent.task

import de.heckenmann.visualagent.testsupport.DatabaseTest
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.todo.TodoUpdateCommand
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Adding acceptance criteria to legacy work cannot bypass server-owned completion. */
@DatabaseTest
class TaskContractAttachmentTest {
    private val contract =
        TaskContract(
            objective = "Produce the required evidence",
            deliverables = listOf(TaskDeliverable("output", "Required output", "answer.txt")),
            criteria = listOf(TaskCriterion("exists", "Output exists", "output", TaskCheckMethod.FILE_EXISTS)),
        )

    @Test
    fun `attaching a contract reopens completed legacy work even with the stale completed status`() {
        KnowledgeDbTestFactory.create("jdbc:h2:mem:attach-completed-${UUID.randomUUID()}").use { db ->
            val manager = TodoManager(db.todoStore, TodoEventBus())
            for (submittedStatus in listOf(null, TodoStatus.COMPLETED)) {
                val todo = manager.add("Legacy task $submittedStatus")
                assertTrue(manager.updateStatus(todo.id, TodoStatus.COMPLETED))
                assertTrue(
                    manager.update(TodoUpdateCommand(todo.id, status = submittedStatus, contract = contract, replaceContract = true)),
                )
                val updated = manager.getById(todo.id)!!
                assertEquals(TodoStatus.PENDING, updated.status)
                assertEquals(contract, updated.contract)
                assertNull(updated.completedAt)
                assertNull(updated.acceptedResult)
                assertFalse(manager.updateStatus(todo.id, TodoStatus.COMPLETED))
            }
        }
    }

    @Test
    fun `a combined contract attachment and manual completion is rejected atomically`() {
        KnowledgeDbTestFactory.create("jdbc:h2:mem:attach-pending-${UUID.randomUUID()}").use { db ->
            val manager = TodoManager(db.todoStore, TodoEventBus())
            for (status in listOf(TodoStatus.PENDING, TodoStatus.IN_PROGRESS, TodoStatus.CANCELLED)) {
                val todo = manager.add("Legacy task $status")
                assertTrue(manager.updateStatus(todo.id, status))
                val original = manager.getById(todo.id)!!
                assertFalse(
                    manager.update(TodoUpdateCommand(todo.id, status = TodoStatus.COMPLETED, contract = contract, replaceContract = true)),
                )
                assertEquals(original, manager.getById(todo.id))
            }
        }
    }
}
