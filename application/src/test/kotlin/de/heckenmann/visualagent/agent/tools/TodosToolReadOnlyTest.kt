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
class TodosToolReadOnlyTest {
    @Test
    fun `sub-agent can read todos but cannot change lifecycle`() {
        val tempDb =
            createTempDirectory("visual-agent-todos-tool-read-only-worker")
                .resolve("todos-tool.db")
                .toString()
        val db = KnowledgeDbTestFactory.create(tempDb)
        try {
            val manager = mockk<AgentManager>()
            every { manager.getSubAgent(any()) } returns SubAgent(id = "agent-1", name = "Coder", role = "Implementation")
            every { manager.todoManager } returns TodoManager(db, TodoEventBus())
            val tool = TodosTool(db, db, manager)
            val added = tool.execute(jsonReadOnly("action" to "add", "description" to "Protected task", "assignedAgentId" to "agent-1"))
            val id = added.content.removePrefix("Added todo ")
            val workerContext = mapOf<String, Any>("agentId" to "agent-1")

            assertFalse(tool.execute(jsonReadOnly("action" to "complete", "id" to id), workerContext).success)
            assertFalse(
                tool
                    .execute(
                        jsonReadOnly("action" to "update", "id" to id, "status" to "COMPLETED"),
                        workerContext,
                    ).success,
            )
            assertTrue(tool.execute(jsonReadOnly("action" to "list"), workerContext).success)
            assertEquals(TodoStatus.PENDING, db.listTodos().single().status)
        } finally {
            db.close()
        }
    }
}

private fun jsonReadOnly(vararg pairs: Pair<String, Any>): String =
    pairs.joinToString(", ", prefix = "{ ", postfix = " }") { (key, value) ->
        val encoded = if (value is String) "\"$value\"" else value.toString()
        "\"$key\": $encoded"
    }
