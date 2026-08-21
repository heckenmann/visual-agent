package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.testsupport.TestPersistence
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Covers todo reordering edge cases. */
@de.heckenmann.visualagent.testsupport.DatabaseTest
class TodosToolReorderTest {
    @Test
    fun `reorder before action changes position`() {
        val db = createDatabase("reorder-before")
        try {
            val tool = createTool(db)
            val first = tool.execute(reorderJson("action" to "add", "description" to "A", "assignedAgentId" to "agent-1"))
            val firstId = first.content.removePrefix("Added todo ")
            tool.execute(reorderJson("action" to "add", "description" to "B", "assignedAgentId" to "agent-1"))
            val secondId = db.listTodos().first { it.description == "B" }.id

            val result = tool.execute(reorderJson("action" to "reorder", "id" to firstId, "before" to secondId))

            assertTrue(result.success)
            assertEquals(0, db.listTodos().first { it.description == "A" }.position)
        } finally {
            db.close()
        }
    }

    @Test
    fun `reorder fails for missing todo`() {
        val db = createDatabase("reorder-missing")
        try {
            assertFalse(createTool(db).execute(reorderJson("action" to "reorder", "id" to "missing", "position" to 0)).success)
        } finally {
            db.close()
        }
    }

    private fun createDatabase(name: String): TestPersistence =
        KnowledgeDbTestFactory
            .create(createTempDirectory("visual-agent-todos-tool-$name").resolve("todos-tool.db").toString())

    private fun createTool(db: TestPersistence): TodosTool {
        val manager = mockk<AgentManager>()
        every { manager.getSubAgent(any()) } returns SubAgent(id = "agent-1", name = "Coder", role = "Implementation")
        every { manager.todoManager } returns TodoManager(db, TodoEventBus())
        return TodosTool(db, db, manager)
    }
}

private fun reorderJson(vararg pairs: Pair<String, Any>): String {
    val entries =
        pairs.joinToString(", ") { (key, value) ->
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
