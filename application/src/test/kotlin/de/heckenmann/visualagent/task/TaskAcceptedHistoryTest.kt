package de.heckenmann.visualagent.task

import de.heckenmann.visualagent.testsupport.DatabaseTest
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.todo.TodoUpdateCommand
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Accepted audit records survive contract edits and late non-accepted callbacks. */
@DatabaseTest
class TaskAcceptedHistoryTest {
    @Test
    fun `acceptance is durable and reopening preserves immutable history`() {
        KnowledgeDbTestFactory.create("jdbc:h2:mem:accepted-history-${UUID.randomUUID()}").use { db ->
            val root = Files.createTempDirectory("accepted-history")
            try {
                val manager =
                    TodoManager(
                        db.todoStore,
                        de.heckenmann.visualagent.todo
                            .TodoEventBus(),
                    )
                val contract = TaskContract(objective = "Initial objective")
                val todo = manager.addIfAbsent("History task", "worker", contract).todo
                val claimed = manager.claimPendingTodo(todo.id, "worker")!!
                val result =
                    TaskAttemptResult(
                        todo.id,
                        "worker",
                        "execution",
                        1,
                        1,
                        "worker/1",
                        WorkerHandoff("Original report", TaskOutcome.PRODUCED),
                        emptyList(),
                        emptyList(),
                        contractSnapshot = claimed.contract,
                    )
                val attempts = TaskAttemptStore(db.databaseClient)
                attempts.save(result).block()
                assertTrue(
                    db.todoStore.updateTodoIfCurrent(
                        claimed,
                        claimed.copy(
                            status = TodoStatus.COMPLETED,
                            acceptedResult = result.copy(accepted = true),
                        ),
                    ),
                )
                val service = TaskExecutionService(TaskArtifactVerifier(WorkspaceFileService(db.workspaceFileStore, root)), attempts)
                assertTrue(service.attempts(todo.id, 0, 10, manager.getById(todo.id)!!.acceptedResult).single().currentAccepted)
                assertTrue(manager.update(TodoUpdateCommand(todo.id, status = TodoStatus.COMPLETED)))
                assertTrue(
                    manager.update(
                        TodoUpdateCommand(
                            todo.id,
                            status = TodoStatus.COMPLETED,
                            contract = contract.copy(objective = "New objective"),
                            replaceContract = true,
                        ),
                    ),
                )
                val reopened = manager.getById(todo.id)!!
                assertEquals(TodoStatus.PENDING, reopened.status)
                assertNull(reopened.acceptedResult)
                assertFalse(manager.update(TodoUpdateCommand(todo.id, status = TodoStatus.COMPLETED)))
                attempts.save(result.copy(reported = result.reported.copy(summary = "Late callback"))).block()
                val historical = service.attempts(todo.id, 0, 10, null).single()
                assertTrue(historical.accepted)
                assertFalse(historical.currentAccepted)
                assertEquals("Original report", historical.reported.summary)
            } finally {
                root.toFile().deleteRecursively()
            }
        }
    }
}
