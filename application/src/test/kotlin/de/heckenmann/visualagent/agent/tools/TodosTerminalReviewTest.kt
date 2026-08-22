package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoStatus
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@de.heckenmann.visualagent.testsupport.DatabaseTest
class TodosTerminalReviewTest {
    @Test
    fun `automatic terminal review cannot mutate or recreate a terminal todo`() {
        val tempDb = createTempDirectory("visual-agent-todos-tool-terminal-review").resolve("todos-tool.db").toString()
        val db = KnowledgeDbTestFactory.create(tempDb)
        try {
            val manager = mockk<AgentManager>()
            every { manager.getSubAgent(any()) } returns SubAgent(id = "agent-1", name = "Coder", role = "Implementation")
            every { manager.todoManager } returns TodoManager(db, TodoEventBus())
            val tool = TodosTool(db, db, manager)
            val added = tool.execute(json("action" to "add", "description" to "Run script", "assignedAgentId" to "agent-1"))
            val id = added.content.removePrefix("Added todo ")
            db.saveTodo(db.listTodos().single().copy(status = TodoStatus.COMPLETED))
            val reviewContext = mapOf<String, Any>("requestId" to "todo-trigger-$id")
            val update =
                tool.execute(
                    json("action" to "update", "id" to id, "description" to "Run script again", "status" to "PENDING"),
                    reviewContext,
                )
            val add =
                tool.execute(
                    json(
                        "action" to "add",
                        "description" to "Run script again",
                        "assignedAgentId" to "agent-1",
                    ),
                    reviewContext,
                )

            assertFalse(update.success)
            assertTrue(update.error!!.contains("cannot modify completed or cancelled"))
            assertFalse(add.success)
            assertTrue(add.error!!.contains("cannot create or retry"))
            assertEquals(TodoStatus.COMPLETED, db.listTodos().single().status)
        } finally {
            db.close()
        }
    }
}

private fun json(vararg pairs: Pair<String, Any>): String =
    "{ " +
        pairs.joinToString(", ") { (key, value) ->
            val encoded = if (value is String) "\"$value\"" else value.toString()
            "\"$key\": $encoded"
        } +
        " }"
