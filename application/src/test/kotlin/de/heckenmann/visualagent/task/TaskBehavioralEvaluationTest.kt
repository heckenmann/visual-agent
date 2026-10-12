package de.heckenmann.visualagent.task

import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.agent.tools.VisualAgentTool
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.agent.tools.success
import de.heckenmann.visualagent.orchestration.CoordinatorFixture
import de.heckenmann.visualagent.orchestration.buildFixture
import de.heckenmann.visualagent.testsupport.DatabaseTest
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Files
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Versioned scripted corpus exercises the production coordinator, database, tool boundary and artifact verifier offline. */
@DatabaseTest
class TaskBehavioralEvaluationTest {
    @Serializable
    private data class Case(
        val id: String,
        val write: Boolean,
        val content: String,
        val expected: String,
        val outcome: TaskOutcome,
        val terminal: TodoStatus,
        val accepted: Boolean,
        val denyWrites: Boolean = false,
        val method: TaskCheckMethod = TaskCheckMethod.TEXT_CONTAINS,
        val executionTool: String? = null,
    )

    @TestFactory
    fun `offline behavioral corpus v1`(): List<DynamicTest> {
        val corpus = javaClass.getResource("/task-evals/v1.json")!!.readText()
        return Json.decodeFromString<List<Case>>(corpus).map { case -> DynamicTest.dynamicTest(case.id) { execute(case) } }
    }

    private fun execute(case: Case) =
        runBlocking {
            KnowledgeDbTestFactory.create("jdbc:h2:mem:task-eval-${UUID.randomUUID()}").use { db ->
                val root = Files.createTempDirectory("task-eval")
                val workspace = WorkspaceFileService(db.workspaceFileStore, root)
                val attempts = TaskAttemptStore(db.databaseClient)
                val service = TaskExecutionService(TaskArtifactVerifier(workspace), attempts)
                val contract =
                    TaskContract(
                        objective = "Produce a verified fact",
                        deliverables = listOf(TaskDeliverable("answer", "Grounded answer", "results/answer.txt")),
                        criteria =
                            listOf(
                                TaskCriterion(
                                    "fact",
                                    "Contains the required fact",
                                    "answer",
                                    case.method,
                                    case.expected,
                                    executionTool = case.executionTool,
                                ),
                            ),
                        policy =
                            TaskPolicy(
                                resources = listOf("results"),
                                actions =
                                    if (case.denyWrites) {
                                        mapOf(
                                            "workspace:file" to listOf("readText"),
                                        )
                                    } else {
                                        emptyMap()
                                    },
                            ),
                    )
                val handoff = WorkerHandoff("Produced answer", case.outcome, listOf(TaskArtifact("answer", "results/answer.txt")))
                val tool =
                    object : VisualAgentTool {
                        override val definition =
                            de.heckenmann.visualagent.agent.tools.api.ToolDefinition(
                                de.heckenmann.visualagent.agent.tools.api
                                    .ToolId("workspace:file"),
                                "workspace_file",
                                "Corpus writer",
                                "{}",
                            )

                        override fun execute(
                            inputJson: String,
                            context: Map<String, Any>,
                        ): ToolResult {
                            workspace.writeText("results/answer.txt", case.content)
                            return success("workspace:file", "File written")
                        }
                    }
                val registry = ToolRegistry(listOf(tool), ToolEventBus())
                lateinit var fixture: CoordinatorFixture
                fixture =
                    buildFixture(
                        todoStore = db.todoStore,
                        taskExecutionService = service,
                        enabledWorkerTools = setOf(ToolId("workspace:file")),
                        responseContent =
                            kotlinx.serialization.json.Json
                                .encodeToString(WorkerHandoff.serializer(), handoff),
                        reviewContent =
                            if (case.method ==
                                TaskCheckMethod.MANUAL
                            ) {
                                """{"verdict":"APPROVED","feedback":"Scripted reviewer approval","criteria":[{"criterionId":"fact","state":"PASSED","detail":"Claims tests passed"}]}"""
                            } else {
                                """{"verdict":"APPROVED","feedback":"Scripted reviewer approval","criteria":[]}"""
                            },
                        onWorkerStreamStarted = {
                            if (case.write) {
                                registry.executeBlockingResult(
                                    tool,
                                    """{"action":"writeText","path":"results/answer.txt"}""",
                                    fixture.providerRequests.last().metadata,
                                )
                            }
                        },
                    )
                fixture.putSubAgent(SubAgent("worker", "Arbeiter", "Write answers", config = AgentConfig(maxRetries = 1)))
                val startedAt = System.nanoTime()
                try {
                    val todo = fixture.todoManager.addIfAbsent(contract.objective, "worker", contract).todo
                    assertTrue(fixture.coordinator.startTodo(todo.id))
                    withTimeout(15_000) { fixture.awaitTodoStatus(todo.id, case.terminal) }
                    val stored = fixture.todoManager.getById(todo.id)!!
                    assertEquals(case.accepted, stored.acceptedResult?.accepted == true)
                    val records = attempts.list(todo.id).collectList().block(Duration.ofSeconds(5))!!
                    assertEquals(1, records.size)
                    assertEquals("worker", records.single().agentId)
                    assertEquals(TaskPrompts.WORKER_VERSION, records.single().promptVersion)
                    assertEquals(1L, records.single().contractRevision)
                    val artifactPath = workspace.workspaceRoot().resolve("results/answer.txt")
                    if (case.write && !case.denyWrites) {
                        assertEquals(case.content, Files.readString(artifactPath))
                    } else {
                        assertTrue(Files.notExists(artifactPath), "Denied/missing writes must not produce artifacts")
                    }
                    if (case.accepted) {
                        assertTrue(Files.readString(artifactPath).contains(case.expected))
                        assertTrue(stored.acceptedResult!!.observations.all { it.state == TaskCheckState.PASSED })
                        assertNotNull(
                            stored.acceptedResult
                                .artifacts
                                .single()
                                .sha256,
                        )
                    }
                    assertTrue(fixture.providerRequests.filter { it.metadata["sessionId"] == "review" }.all { it.enabledTools.isEmpty() })
                    val report =
                        kotlinx.serialization.json.buildJsonObject {
                            put("mode", kotlinx.serialization.json.JsonPrimitive("SCRIPTED_OFFLINE"))
                            put("corpus", kotlinx.serialization.json.JsonPrimitive("v1"))
                            put("scenario", kotlinx.serialization.json.JsonPrimitive(case.id))
                            put(
                                "label",
                                kotlinx.serialization.json.JsonPrimitive(System.getProperty("visualagent.taskEval.label", "candidate")),
                            )
                            put("promptVersion", kotlinx.serialization.json.JsonPrimitive(TaskPrompts.WORKER_VERSION))
                            put("model", kotlinx.serialization.json.JsonPrimitive("scripted-test-provider"))
                            put("trial", kotlinx.serialization.json.JsonPrimitive(1))
                            put("state", kotlinx.serialization.json.JsonPrimitive(stored.status.name))
                            put("accepted", kotlinx.serialization.json.JsonPrimitive(stored.acceptedResult != null))
                            put(
                                "incorrectCompletion",
                                kotlinx.serialization.json.JsonPrimitive(stored.acceptedResult != null && !case.accepted),
                            )
                            put("wrongAssignment", kotlinx.serialization.json.JsonPrimitive(records.any { it.agentId != "worker" }))
                            put("duplicateAgentCreation", kotlinx.serialization.json.JsonPrimitive(fixture.subAgents.size != 1))
                            put("attempts", kotlinx.serialization.json.JsonPrimitive(records.size))
                            put("modelRequests", kotlinx.serialization.json.JsonPrimitive(fixture.providerRequests.size))
                            put("toolCalls", kotlinx.serialization.json.JsonPrimitive(records.sumOf { it.toolEvidence.size }))
                            put("elapsedMillis", kotlinx.serialization.json.JsonPrimitive((System.nanoTime() - startedAt) / 1_000_000))
                            put("usage", kotlinx.serialization.json.JsonPrimitive("UNAVAILABLE"))
                            put("cost", kotlinx.serialization.json.JsonPrimitive("UNAVAILABLE"))
                            put("actualModelTrials", kotlinx.serialization.json.JsonPrimitive("NOT_RUN"))
                        }
                    val output =
                        java.nio.file.Path
                            .of("build/reports/task-evaluations/offline-${case.id}.json")
                    Files.createDirectories(output.parent)
                    Files.writeString(output, report.toString())
                } finally {
                    fixture.cancel()
                    root.toFile().deleteRecursively()
                }
            }
        }
}
