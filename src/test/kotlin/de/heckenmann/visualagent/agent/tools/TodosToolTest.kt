package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoPriority
import de.heckenmann.visualagent.todo.TodoStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TodosToolTest {
    @Test
    fun `count action returns total and status distribution`() {
        val tempDb = createTempDirectory("visual-agent-todos-tool-test").resolve("todos-tool.db").toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        try {
            db.saveTodo(
                Todo(
                    id = "t1",
                    description = "Pending task",
                    status = TodoStatus.PENDING,
                    priority = TodoPriority.MEDIUM,
                    createdAt = Instant.now(),
                ),
            )
            db.saveTodo(
                Todo(
                    id = "t2",
                    description = "In progress task",
                    status = TodoStatus.IN_PROGRESS,
                    priority = TodoPriority.HIGH,
                    createdAt = Instant.now(),
                ),
            )
            db.saveTodo(
                Todo(
                    id = "t3",
                    description = "Done task",
                    status = TodoStatus.COMPLETED,
                    priority = TodoPriority.LOW,
                    createdAt = Instant.now(),
                ),
            )

            val tool = TodosTool(db)
            val result = tool.execute("""{"action":"count"}""")

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
        val tempDb = createTempDirectory("visual-agent-todos-tool-lifecycle").resolve("todos-tool.db").toString()
        val db =
            de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
                .create(tempDb)
        try {
            val tool = TodosTool(db)
            assertEquals("No todos.", tool.execute("""{"action":"list"}""").content)

            val added = tool.execute("""{"action":"add","description":"Ship feature","priority":"high"}""")
            assertTrue(added.success)
            val id = added.content.removePrefix("Added todo ")
            assertTrue(tool.execute("""{"action":"list"}""").content.contains("Ship feature"))

            assertTrue(
                tool
                    .execute(
                        """{"action":"update","id":"$id","description":"Ship tested feature","status":"in_progress","assignedAgentId":"coder"}""",
                    ).success,
            )
            assertTrue(tool.execute("""{"action":"complete","id":"$id"}""").success)
            assertTrue(db.listTodos().single().completedAt != null)
            assertTrue(tool.execute("""{"action":"cancel","id":"$id"}""").success)
            assertTrue(tool.execute("""{"action":"remove","id":"$id"}""").success)
            assertTrue(db.listTodos().isEmpty())

            assertFalse(tool.execute("""{"action":"update","id":"missing"}""").success)
            assertFalse(tool.execute("""{"action":"complete","id":"missing"}""").success)
            assertFalse(tool.execute("""{"action":"invalid"}""").success)
        } finally {
            db.close()
        }
    }
}
