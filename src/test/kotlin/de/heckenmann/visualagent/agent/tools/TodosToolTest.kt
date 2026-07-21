package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.testsupport.TestPersistence
import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoStatus
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TodosToolTest {
    private fun createTool(db: TestPersistence): TodosTool {
        val manager = mockk<AgentManager>()
        every { manager.getSubAgent(any()) } returns SubAgent(id = "agent-1", name = "Coder", role = "Implementation")
        every { manager.todoManager } returns TodoManager(db, TodoEventBus())
        return TodosTool(db, db, manager)
    }

    @Test
    fun `count action returns total and status distribution`() {
        val tempDb =
            createTempDirectory("visual-agent-todos-tool-test")
                .resolve("todos-tool.db")
                .toString()
        val db =
            KnowledgeDbTestFactory
                .create(tempDb)
        try {
            db.saveTodo(
                Todo(
                    id = "t1",
                    description = "Pending task",
                    status = TodoStatus.PENDING,
                    position = 0,
                    assignedAgentId = "agent-1",
                    createdAt = Instant.now(),
                ),
            )
            db.saveTodo(
                Todo(
                    id = "t2",
                    description = "In progress task",
                    status = TodoStatus.IN_PROGRESS,
                    position = 1,
                    assignedAgentId = "agent-1",
                    createdAt = Instant.now(),
                ),
            )
            db.saveTodo(
                Todo(
                    id = "t3",
                    description = "Done task",
                    status = TodoStatus.COMPLETED,
                    position = 2,
                    assignedAgentId = "agent-1",
                    createdAt = Instant.now(),
                ),
            )

            val tool = createTool(db)
            val result = tool.execute(json("action" to "count"))

            assertTrue(result.success)
            assertEquals("todos", result.toolId)
            assertTrue(result.content.contains("total=3"))
            assertTrue(result.content.contains("open=1"))
            assertTrue(result.content.contains("in_progress=1"))
            assertTrue(result.content.contains("completed=1"))
            assertTrue(result.content.contains("cancelled=0"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `todo actions cover the complete persisted lifecycle`() {
        val tempDb =
            createTempDirectory("visual-agent-todos-tool-lifecycle")
                .resolve("todos-tool.db")
                .toString()
        val db =
            KnowledgeDbTestFactory
                .create(tempDb)
        try {
            val tool = createTool(db)
            assertEquals("No todos.", tool.execute(json("action" to "list")).content)

            val added = tool.execute(json("action" to "add", "description" to "Ship feature", "assignedAgentId" to "agent-1"))
            assertTrue(added.success)
            val id = added.content.removePrefix("Added todo ")
            assertTrue(tool.execute(json("action" to "list")).content.contains("Ship feature"))

            assertTrue(
                tool
                    .execute(
                        json(
                            "action" to "update",
                            "id" to id,
                            "description" to "Ship tested feature",
                            "status" to "in_progress",
                            "assignedAgentId" to "agent-1",
                        ),
                    ).success,
            )
            assertTrue(tool.execute(json("action" to "complete", "id" to id)).success)
            assertTrue(db.listTodos().single().completedAt != null)
            assertTrue(tool.execute(json("action" to "remove", "id" to id)).success)
            assertTrue(db.listTodos().isEmpty())

            assertFalse(tool.execute(json("action" to "update", "id" to "missing")).success)
            assertFalse(tool.execute(json("action" to "complete", "id" to "missing")).success)
            assertFalse(tool.execute(json("action" to "invalid")).success)
        } finally {
            db.close()
        }
    }

    @Test
    fun `add requires assignedAgentId referencing an existing agent`() {
        val tempDb =
            createTempDirectory("visual-agent-todos-tool-add-validation")
                .resolve("todos-tool.db")
                .toString()
        val db =
            KnowledgeDbTestFactory
                .create(tempDb)
        try {
            val manager = mockk<AgentManager>()
            every { manager.getSubAgent("missing") } returns null
            every { manager.getSubAgent("agent-1") } returns SubAgent(id = "agent-1", name = "Coder", role = "Implementation")
            every { manager.todoManager } returns TodoManager(db, TodoEventBus())
            val tool = TodosTool(db, db, manager)

            val missing = tool.execute(json("action" to "add", "description" to "No agent"))
            assertFalse(missing.success)
            assertTrue((missing.error ?: missing.content).contains("assignedAgentId is required"))

            val invalid = tool.execute(json("action" to "add", "description" to "Bad agent", "assignedAgentId" to "missing"))
            assertFalse(invalid.success)
            assertTrue(invalid.error!!.contains("must reference an existing sub-agent"))

            val valid = tool.execute(json("action" to "add", "description" to "Good agent", "assignedAgentId" to "agent-1"))
            assertTrue(valid.success)
        } finally {
            db.close()
        }
    }

    @Test
    fun `update rejects assignedAgentId referencing missing agent`() {
        val tempDb =
            createTempDirectory("visual-agent-todos-tool-update-validation")
                .resolve("todos-tool.db")
                .toString()
        val db =
            KnowledgeDbTestFactory
                .create(tempDb)
        try {
            val manager = mockk<AgentManager>()
            every { manager.getSubAgent("missing") } returns null
            every { manager.getSubAgent("agent-1") } returns SubAgent(id = "agent-1", name = "Coder", role = "Implementation")
            every { manager.todoManager } returns TodoManager(db, TodoEventBus())
            val tool = TodosTool(db, db, manager)
            val added = tool.execute(json("action" to "add", "description" to "Task", "assignedAgentId" to "agent-1"))
            val id = added.content.removePrefix("Added todo ")

            val invalid = tool.execute(json("action" to "update", "id" to id, "assignedAgentId" to "missing"))
            assertFalse(invalid.success)
            assertTrue(invalid.error!!.contains("must reference an existing sub-agent"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `get-result returns memory summary for todo`() {
        val tempDb =
            createTempDirectory("visual-agent-todos-tool-get-result")
                .resolve("todos-tool.db")
                .toString()
        val db =
            KnowledgeDbTestFactory
                .create(tempDb)
        try {
            val tool = createTool(db)
            val added = tool.execute(json("action" to "add", "description" to "Task", "assignedAgentId" to "agent-1"))
            val id = added.content.removePrefix("Added todo ")
            db.saveStructuredKnowledge(subject = "todo:$id", summary = "Result summary", nextSteps = "Next")

            val result = tool.execute(json("action" to "get-result", "id" to id))

            assertTrue(result.success)
            assertTrue(result.content.contains("Result summary"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `get-result fails when no memory exists`() {
        val tempDb =
            createTempDirectory("visual-agent-todos-tool-get-result-missing")
                .resolve("todos-tool.db")
                .toString()
        val db =
            KnowledgeDbTestFactory
                .create(tempDb)
        try {
            val tool = createTool(db)
            val result = tool.execute(json("action" to "get-result", "id" to "missing"))
            assertFalse(result.success)
            assertTrue(result.error!!.contains("No result available"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `reorder action changes position`() {
        val tempDb =
            createTempDirectory("visual-agent-todos-tool-reorder")
                .resolve("todos-tool.db")
                .toString()
        val db =
            KnowledgeDbTestFactory
                .create(tempDb)
        try {
            val tool = createTool(db)
            tool.execute(json("action" to "add", "description" to "A", "assignedAgentId" to "agent-1"))
            tool.execute(json("action" to "add", "description" to "B", "assignedAgentId" to "agent-1"))
            val addedC = tool.execute(json("action" to "add", "description" to "C", "assignedAgentId" to "agent-1"))
            val idC = addedC.content.removePrefix("Added todo ")

            val result = tool.execute(json("action" to "reorder", "id" to idC, "position" to 0))
            assertTrue(result.success)
            val listResult = tool.execute(json("action" to "list"))
            assertTrue(listResult.content.contains("position=0"))
            assertTrue(listResult.content.contains("C"))
            assertEquals(0, db.listTodos().first { it.description == "C" }.position)
        } finally {
            db.close()
        }
    }

    @Test
    fun `reorder before action changes position`() {
        val tempDb =
            createTempDirectory("visual-agent-todos-tool-reorder-before")
                .resolve("todos-tool.db")
                .toString()
        val db =
            KnowledgeDbTestFactory
                .create(tempDb)
        try {
            val tool = createTool(db)
            val a = tool.execute(json("action" to "add", "description" to "A", "assignedAgentId" to "agent-1"))
            val idA = a.content.removePrefix("Added todo ")
            tool.execute(json("action" to "add", "description" to "B", "assignedAgentId" to "agent-1"))
            val idB = db.listTodos().first { it.description == "B" }.id

            val result = tool.execute(json("action" to "reorder", "id" to idA, "before" to idB))
            assertTrue(result.success)
            assertEquals(0, db.listTodos().first { it.description == "A" }.position)
        } finally {
            db.close()
        }
    }

    @Test
    fun `reorder fails for missing todo`() {
        val tempDb =
            createTempDirectory("visual-agent-todos-tool-reorder-missing")
                .resolve("todos-tool.db")
                .toString()
        val db =
            KnowledgeDbTestFactory
                .create(tempDb)
        try {
            val tool = createTool(db)
            assertFalse(tool.execute(json("action" to "reorder", "id" to "missing", "position" to 0)).success)
        } finally {
            db.close()
        }
    }
}

private fun json(vararg pairs: Pair<String, Any>): String {
    val entries =
        pairs
            .joinToString(", ") { (key, value) ->
                val encoded =
                    when (value) {
                        is String -> "\"$value\""
                        is Number -> value.toString()
                        else -> "\"${value.toString().replace("\"", "\\\"")}\""
                    }
                "\"$key\": $encoded"
            }
    return "{ $entries }"
}
