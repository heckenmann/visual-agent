package de.heckenmann.visualagent.task

import de.heckenmann.visualagent.testsupport.DatabaseTest
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.todo.TodoUpdateCommand
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DatabaseTest
class TaskEvidenceTest {
    @Test
    fun `quote verification reads current bytes from both files`() {
        KnowledgeDbTestFactory.create("jdbc:h2:mem:evidence-${UUID.randomUUID()}").use { db ->
            val root = Files.createTempDirectory("task-evidence")
            try {
                val workspace = WorkspaceFileService(db.workspaceFileStore, root)
                workspace.writeText("source.txt", "Real source quote")
                workspace.writeText("answer.txt", "Real source quote")
                val contract =
                    TaskContract(
                        objective = "Quote source",
                        inputs = listOf(TaskInput("source", path = "source.txt")),
                        deliverables = listOf(TaskDeliverable("answer", "Answer", "answer.txt")),
                        criteria =
                            listOf(
                                TaskCriterion(
                                    "quote",
                                    "Grounded quote",
                                    "answer",
                                    TaskCheckMethod.SOURCE_QUOTE,
                                    "Real source quote",
                                    "source",
                                ),
                            ),
                    )
                val verifier = TaskArtifactVerifier(workspace)
                val observed = verifier.observe(contract)
                assertEquals(TaskCheckState.PASSED, observed.second.single().state)
                Files.writeString(workspace.workspaceRoot().resolve("source.txt"), "Changed source")
                assertEquals(
                    TaskCheckState.FAILED,
                    verifier
                        .observe(contract)
                        .second
                        .single()
                        .state,
                )
                assertFalse(
                    verifier.unchanged(contract, observed.first),
                    "Changed source invalidates approval even when output is unchanged",
                )
                Files.writeString(workspace.workspaceRoot().resolve("answer.txt"), "Changed answer")
                assertFalse(verifier.unchanged(contract, observed.first))
                assertTrue(verifier.reviewEvidence(contract).contains("Changed answer"))
                val manual = contract.copy(criteria = listOf(TaskCriterion("manual", "Inspect content", "answer")))
                assertEquals(
                    TaskCheckState.NOT_CHECKED,
                    verifier
                        .observe(manual)
                        .second
                        .single()
                        .state,
                )
            } finally {
                root.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun `contract edits invalidate stale completion and survive archival`() {
        KnowledgeDbTestFactory.create("jdbc:h2:mem:contract-edit-${UUID.randomUUID()}").use { db ->
            val manager =
                TodoManager(
                    db.todoStore,
                    de.heckenmann.visualagent.todo
                        .TodoEventBus(),
                )
            val created = manager.addIfAbsent("Task", "worker", TaskContract(objective = "First")).todo
            val claimed = manager.claimPendingTodo(created.id, "worker")!!
            assertTrue(
                manager.update(
                    TodoUpdateCommand(
                        created.id,
                        contract = claimed.contract!!.copy(objective = "Revised", revision = 900),
                        replaceContract = true,
                    ),
                ),
            )
            val current = manager.getById(created.id)!!
            assertEquals(2L, current.contract!!.revision)
            assertFalse(db.updateTodoIfCurrent(claimed, claimed.copy(status = TodoStatus.COMPLETED)))
            assertFalse(manager.completeTodo(created.id))
            db.deleteTodoAndArchive(current)
            assertEquals(current.contract, db.listDeletedTodos().single().contract)
        }
    }
}
