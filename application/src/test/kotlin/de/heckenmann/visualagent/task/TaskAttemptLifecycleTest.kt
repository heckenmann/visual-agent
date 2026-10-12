package de.heckenmann.visualagent.task

import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.orchestration.buildFixture
import de.heckenmann.visualagent.testsupport.DatabaseTest
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.todo.TodoUpdateCommand
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Actual persisted contract edits and provider failures cannot turn old attempts into accepted output. */
@DatabaseTest
class TaskAttemptLifecycleTest {
    @Test
    fun `delayed review is invalidated by a live contract edit`() =
        runBlocking {
            KnowledgeDbTestFactory.create("jdbc:h2:mem:task-race-${UUID.randomUUID()}").use { db ->
                val root = Files.createTempDirectory("task-race")
                val workspace = WorkspaceFileService(db.workspaceFileStore, root)
                workspace.writeText("answer.txt", "Evidence")
                val attempts = TaskAttemptStore(db.databaseClient)
                val service = TaskExecutionService(TaskArtifactVerifier(workspace), attempts)
                val gate = CompletableDeferred<Unit>()
                val fixture =
                    buildFixture(
                        todoStore = db.todoStore,
                        taskExecutionService = service,
                        reviewResponseGate = gate,
                        responseContent =
                            Json.encodeToString(
                                WorkerHandoff("Done", TaskOutcome.PRODUCED, listOf(TaskArtifact("answer", "answer.txt"))),
                            ),
                        reviewContent = """{"verdict":"APPROVED","feedback":"Looks good","criteria":[]}""",
                    )
                fixture.putSubAgent(SubAgent("worker", "Worker", "Evidence", config = AgentConfig(maxRetries = 1)))
                try {
                    val contract =
                        TaskContract(
                            objective = "Check evidence",
                            deliverables = listOf(TaskDeliverable("answer", "Answer", "answer.txt")),
                            criteria =
                                listOf(
                                    TaskCriterion("fact", "Contains evidence", "answer", TaskCheckMethod.TEXT_CONTAINS, "Evidence"),
                                ),
                        )
                    val todo = fixture.todoManager.addIfAbsent("Check evidence", "worker", contract).todo
                    assertTrue(fixture.coordinator.startTodo(todo.id))
                    withTimeout(15_000) { fixture.awaitReviewStart() }
                    assertTrue(
                        fixture.todoManager.update(
                            TodoUpdateCommand(
                                todo.id,
                                contract =
                                    contract.copy(
                                        objective = "Changed objective",
                                        criteria =
                                            listOf(
                                                TaskCriterion(
                                                    "fact",
                                                    "New evidence",
                                                    "answer",
                                                    TaskCheckMethod.TEXT_CONTAINS,
                                                    "New evidence",
                                                ),
                                            ),
                                    ),
                                replaceContract = true,
                            ),
                        ),
                    )
                    gate.complete(Unit)
                    withTimeout(15_000) { fixture.awaitTodoStatus(todo.id, TodoStatus.CANCELLED) }
                    val current = fixture.todoManager.getById(todo.id)!!
                    assertEquals(2L, current.contract!!.revision)
                    assertNull(current.acceptedResult)
                    assertTrue(
                        attempts
                            .list(todo.id)
                            .collectList()
                            .block()!!
                            .any { it.contractRevision == 1L && !it.accepted },
                    )
                } finally {
                    gate.complete(Unit)
                    fixture.cancel()
                    root.toFile().deleteRecursively()
                }
            }
        }

    @Test
    fun `failure before a worker handoff remains a durable unchecked attempt`() =
        runBlocking {
            KnowledgeDbTestFactory.create("jdbc:h2:mem:task-failure-${UUID.randomUUID()}").use { db ->
                val root = Files.createTempDirectory("task-failure")
                val attempts = TaskAttemptStore(db.databaseClient)
                val service = TaskExecutionService(TaskArtifactVerifier(WorkspaceFileService(db.workspaceFileStore, root)), attempts)
                val fixture = buildFixture(todoStore = db.todoStore, taskExecutionService = service, failingWorkerAttempts = 1)
                fixture.putSubAgent(SubAgent("worker", "Worker", "Evidence", config = AgentConfig(maxRetries = 1)))
                try {
                    val todo =
                        fixture.todoManager
                            .addIfAbsent(
                                "Missing execution",
                                "worker",
                                TaskContract(
                                    objective = "Missing execution",
                                    criteria = listOf(TaskCriterion("check", "Verify execution")),
                                ),
                            ).todo
                    assertTrue(fixture.coordinator.startTodo(todo.id))
                    withTimeout(15_000) { fixture.awaitTodoStatus(todo.id, TodoStatus.CANCELLED) }
                    val record =
                        attempts
                            .list(todo.id)
                            .collectList()
                            .block()!!
                            .single()
                    assertEquals(TaskOutcome.BLOCKED, record.reported.outcome)
                    assertEquals(TaskCheckState.NOT_CHECKED, record.observations.single().state)
                    assertNull(fixture.todoManager.getById(todo.id)!!.acceptedResult)
                } finally {
                    fixture.cancel()
                    root.toFile().deleteRecursively()
                }
            }
        }
}
