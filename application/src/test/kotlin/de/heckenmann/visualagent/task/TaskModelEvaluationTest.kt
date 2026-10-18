package de.heckenmann.visualagent.task

import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.startTodo
import de.heckenmann.visualagent.agent.stopTodo
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** Explicit opt-in actual-provider trials; measured behavior is never confused with offline correctness. */
@SpringBootTest(properties = ["visual-agent.ui.enabled=false"])
@EnabledIfSystemProperty(named = "visualagent.taskEval.live", matches = "true")
class TaskModelEvaluationTest {
    @Autowired private lateinit var manager: AgentManager

    @Autowired private lateinit var workspace: WorkspaceFileService

    @Test
    fun `replay synthetic evidence tasks against explicitly configured models`() =
        runBlocking {
            val model = System.getProperty("visualagent.taskEval.model").orEmpty()
            require(model.isNotBlank()) { "Specify visualagent.taskEval.model; models must be provisioned before evaluation" }
            val profile = System.getProperty("visualagent.taskEval.provider")?.takeIf { it.isNotBlank() }
            val trials = System.getProperty("visualagent.taskEval.trials", "3").toInt().coerceIn(1, 20)
            val label = System.getProperty("visualagent.taskEval.label", "candidate").replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val worker = manager.createAgent("Evaluation worker", "Inspect evidence honestly and return the task handoff.", "coder")
            val reviewer = manager.createAgent("Evaluation reviewer", "Independently review current evidence.", "reviewer")
            val config = AgentConfig(provider = profile, model = model, tools = listOf("workspace:file"), maxRetries = 1, maxTokens = 2_048)
            manager.updateAgent(worker.id, config = config)
            manager.updateAgent(reviewer.id, config = config.copy(tools = emptyList()))
            val cases =
                Json.parseToJsonElement(
                    javaClass.getResource("/task-evals/v1.json")!!.readText(),
                ) as kotlinx.serialization.json.JsonArray
            for (trial in 1..trials) {
                for (element in cases) {
                    val case = element as kotlinx.serialization.json.JsonObject
                    val id = case.getValue("id").toString().trim('"')
                    val content = (case.getValue("content") as kotlinx.serialization.json.JsonPrimitive).content
                    val expected = (case.getValue("expected") as kotlinx.serialization.json.JsonPrimitive).content
                    val directory = "evaluation/${UUID.randomUUID()}"
                    val path = "$directory/answer.txt"
                    if (case.getValue("write").toString() == "true") workspace.writeText(path, content)
                    val contract =
                        TaskContract(
                            objective = "Inspect the existing answer without changing files. Report blocked if it fails the criterion.",
                            deliverables = listOf(TaskDeliverable("answer", "Existing synthetic answer", path)),
                            criteria =
                                listOf(
                                    TaskCriterion(
                                        "fact",
                                        "Contains the required fact",
                                        "answer",
                                        case["method"]?.let { TaskCheckMethod.valueOf(it.toString().trim('"')) }
                                            ?: TaskCheckMethod.TEXT_CONTAINS,
                                        expected,
                                        executionTool = case["executionTool"]?.toString()?.trim('"'),
                                    ),
                                ),
                            policy =
                                TaskPolicy(
                                    actions = mapOf("workspace:file" to listOf("readText", "hash", "info")),
                                    resources = listOf(directory),
                                ),
                            reviewerAgentId = reviewer.id,
                        )
                    val todo = manager.todoManager.addIfAbsent("Evaluation $id trial $trial ${UUID.randomUUID()}", worker.id, contract).todo
                    val start = System.nanoTime()
                    manager.startTodo(todo.id)
                    val terminal =
                        runCatching {
                            withTimeout(90_000) {
                                while (manager.todoManager.getById(todo.id)?.status !in
                                    setOf(TodoStatus.COMPLETED, TodoStatus.CANCELLED)
                                ) {
                                    delay(50)
                                }
                            }
                        }.isSuccess
                    if (!terminal) manager.stopTodo(todo.id)
                    val persisted = manager.todoManager.getById(todo.id)!!
                    val accepted = persisted.acceptedResult
                    val validContent =
                        content.contains(expected) && case.getValue("write").toString() == "true" && case["executionTool"] == null
                    val report =
                        buildJsonObject {
                            put("mode", "ACTUAL_MODEL")
                            put("scenario", id)
                            put("trial", trial)
                            put("label", label)
                            put("provider", profile ?: "configured-session-profile")
                            put("model", model)
                            put("workerPrompt", TaskPrompts.WORKER_VERSION)
                            put("mainPrompt", TaskPrompts.MAIN_VERSION)
                            put("temperature", "provider-default")
                            put("maxTokens", 2_048)
                            put("runtime", if (terminal) "FINISHED" else "TIMEOUT")
                            put("state", persisted.status.name)
                            put("accepted", accepted != null)
                            put("incorrectCompletion", accepted != null && !validContent)
                            put("elapsedMillis", (System.nanoTime() - start) / 1_000_000)
                            put("usage", "UNAVAILABLE")
                            put("cost", "UNAVAILABLE")
                            put(
                                "criterionInspection",
                                accepted
                                    ?.observations
                                    ?.singleOrNull()
                                    ?.state
                                    ?.name ?: "NOT_CHECKED",
                            )
                            put("offlineCorpusMode", "NOT_RUN")
                        }
                    val destination = Path.of("build/reports/task-evaluations/live-$label-$id-$trial.json")
                    Files.createDirectories(destination.parent)
                    Files.writeString(destination, report.toString())
                    if (case.getValue("write").toString() == "true") workspace.deleteFile(workspace.requireFile(null, path).id)
                }
            }
        }

    companion object {
        private val root = Files.createTempDirectory("task-live-evaluation")

        @JvmStatic
        @DynamicPropertySource
        fun isolatedDatabase(registry: DynamicPropertyRegistry) {
            registry.add("visual-agent.db.path") { root.resolve("evaluation").toString() }
        }
    }
}
