package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.TodoToolPort
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolTodo
import de.heckenmann.visualagent.agent.tools.api.ToolTodoCreation
import de.heckenmann.visualagent.task.TaskAttemptResult
import de.heckenmann.visualagent.task.TaskContract
import de.heckenmann.visualagent.task.TaskOutcome
import de.heckenmann.visualagent.task.TaskToolObservation
import de.heckenmann.visualagent.task.WorkerHandoff
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.springframework.beans.factory.ObjectProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class TodoContractNameBoundaryTest {
    @Test
    fun `todo tool persists ids and returns function names for contracts and attempt evidence`() {
        val workspace =
            object : VisualAgentTool {
                override val definition = ToolDefinition(ToolId("workspace:file"), "workspace_file", "Workspace", "{}")

                override fun execute(
                    inputJson: String,
                    context: Map<String, Any>,
                ) = success("workspace:file", "OK")
            }
        val reference = mockk<ObjectProvider<ToolRegistry>>()
        every { reference.getObject() } returns ToolRegistry(listOf(workspace), ToolEventBus())
        val port = mockk<TodoToolPort>()
        var stored: TaskContract? = null
        every { port.agentExists("worker") } returns true
        every { port.start("todo") } returns true
        every { port.addIfAbsent("Task", "worker", any()) } answers {
            stored = thirdArg()
            ToolTodoCreation(ToolTodo("todo", "Task", "PENDING", 0, "worker", stored), true)
        }
        every { port.list() } answers { listOf(ToolTodo("todo", "Task", "PENDING", 0, "worker", stored)) }
        val tool = TodosTool(port, reference)
        tool.execute(
            """{"action":"add","description":"Task","assignedAgentId":"worker","contract":{"objective":"Task","requiredOperations":[{"tool":"workspace_file","action":"writeText"}]}}""",
            emptyMap(),
        )
        assertEquals("workspace:file", stored!!.requiredOperations.single().tool)
        val exported = tool.execute("""{"action":"get-contract","id":"todo"}""", emptyMap()).content
        assertFalse(exported.contains("workspace:file"))
        assertEquals(
            "workspace_file",
            Json
                .decodeFromString<TaskContract>(exported)
                .requiredOperations
                .single()
                .tool,
        )
        every { port.attempts("todo", 0, 1) } returns
            listOf(
                TaskAttemptResult(
                    "todo",
                    "worker",
                    "execution",
                    1,
                    1,
                    "worker/1",
                    WorkerHandoff("Done", TaskOutcome.PRODUCED),
                    emptyList(),
                    emptyList(),
                    listOf(TaskToolObservation("workspace:file", true, false)),
                    contractSnapshot = stored,
                ),
            )
        val attempt = tool.execute("""{"action":"get-attempts","id":"todo"}""", emptyMap()).content
        assertFalse(attempt.contains("workspace:file"))
        assertEquals(
            "workspace_file",
            Json
                .parseToJsonElement(attempt)
                .jsonObject["attempts"]!!
                .jsonArray
                .single()
                .jsonObject["toolEvidence"]!!
                .jsonArray
                .single()
                .jsonObject["tool"]
                .toString()
                .trim('"'),
        )
        every { reference.getObject() } returns ToolRegistry(emptyList(), ToolEventBus())
        assertEquals(exported, tool.execute("""{"action":"get-contract","id":"todo"}""", emptyMap()).content)
        assertEquals(attempt, tool.execute("""{"action":"get-attempts","id":"todo"}""", emptyMap()).content)
        assertFailsWith<IllegalArgumentException> {
            tool.execute(
                """{"action":"add","description":"Task","assignedAgentId":"worker","contract":{"objective":"Task","requiredOperations":[{"tool":"workspace_file"}]}}""",
                emptyMap(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            tool.execute(
                """{"action":"add","description":"Task","assignedAgentId":"worker","contract":{"objective":"Task","requiredOperations":[{"tool":"unknown_function"}]}}""",
                emptyMap(),
            )
        }
        verify(exactly = 1) { port.addIfAbsent("Task", "worker", any()) }
    }
}
