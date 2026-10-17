package de.heckenmann.visualagent.task

import de.heckenmann.visualagent.testsupport.DatabaseTest
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Actual workspace aliases and rewritten source material cannot bypass task evidence boundaries. */
@DatabaseTest
class TaskResourceBoundaryTest {
    @Test
    fun `declared input outputs can be edited in place without weakening quote sources`() {
        KnowledgeDbTestFactory.create("jdbc:h2:mem:in-place-${UUID.randomUUID()}").use { db ->
            val root = Files.createTempDirectory("in-place")
            try {
                val workspace = WorkspaceFileService(db.workspaceFileStore, root)
                workspace.writeText("code.txt", "broken")
                val verifier = TaskArtifactVerifier(workspace)
                val service = TaskExecutionService(verifier, TaskAttemptStore(db.databaseClient))
                val contract =
                    TaskContract(
                        objective = "Fix code",
                        inputs = listOf(TaskInput("code", path = "code.txt")),
                        deliverables = listOf(TaskDeliverable("fixed", "Fixed code", "code.txt")),
                        criteria = listOf(TaskCriterion("correct", "Fixed", "fixed", TaskCheckMethod.TEXT_CONTAINS, "fixed")),
                    )
                val original = service.inputVersions(contract)
                workspace.writeText("code.txt", "fixed")
                val todo = Todo("edit", "Fix code", contract = contract)
                val report = WorkerHandoff("Fixed", TaskOutcome.PRODUCED, listOf(TaskArtifact("fixed", "code.txt")))
                val result =
                    service
                        .capture(
                            todo,
                            "worker",
                            "edit-execution",
                            1,
                            Json.encodeToString(report),
                            emptyList(),
                            original,
                        ).block()!!
                assertTrue(service.canAccept(todo, result))
                val grounded =
                    contract.copy(
                        criteria =
                            listOf(
                                TaskCriterion("quote", "Quote original", "fixed", TaskCheckMethod.SOURCE_QUOTE, "fixed", "code"),
                            ),
                    )
                assertFalse(service.inputsCurrent(grounded, original))
            } finally {
                root.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun `internal aliases are denied and rewritten sources remain unchecked`() {
        KnowledgeDbTestFactory.create("jdbc:h2:mem:resource-boundary-${UUID.randomUUID()}").use { db ->
            val root = Files.createTempDirectory("resource-boundary")
            try {
                val workspace = WorkspaceFileService(db.workspaceFileStore, root)
                workspace.writeText("allowed/source.txt", "Original quote")
                workspace.writeText("allowed/answer.txt", "Original quote")
                workspace.writeText("other/private.txt", "Outside task")
                val verifier = TaskArtifactVerifier(workspace)
                val contract =
                    TaskContract(
                        objective = "Ground source",
                        inputs = listOf(TaskInput("source", path = "allowed/source.txt")),
                        deliverables = listOf(TaskDeliverable("answer", "Answer", "allowed/answer.txt")),
                        criteria =
                            listOf(
                                TaskCriterion("quote", "Grounded", "answer", TaskCheckMethod.SOURCE_QUOTE, "Original quote", "source"),
                            ),
                        policy = TaskPolicy(resources = listOf("allowed")),
                    )
                Files.createSymbolicLink(workspace.workspaceRoot().resolve("allowed/alias"), workspace.workspaceRoot().resolve("other"))
                assertFalse(verifier.resourceAllowed(contract, "allowed/alias/private.txt"))
                assertTrue(verifier.resourceAllowed(contract, "allowed/new.txt"))
                val service = TaskExecutionService(verifier, TaskAttemptStore(db.databaseClient))
                val original = service.inputVersions(contract)
                workspace.writeText("allowed/source.txt", "Worker rewritten text with Original quote")
                val report = WorkerHandoff("Done", TaskOutcome.PRODUCED, listOf(TaskArtifact("answer", "allowed/answer.txt")))
                val result =
                    service
                        .capture(
                            Todo("todo", "Ground source", contract = contract),
                            "worker",
                            "execution",
                            1,
                            Json.encodeToString(report),
                            emptyList(),
                            original,
                        ).block()!!
                assertEquals(TaskCheckState.NOT_CHECKED, result.observations.single().state)
                assertFalse(service.canAccept(Todo("todo", "Ground source", contract = contract), result))
            } finally {
                root.toFile().deleteRecursively()
            }
        }
    }
}
