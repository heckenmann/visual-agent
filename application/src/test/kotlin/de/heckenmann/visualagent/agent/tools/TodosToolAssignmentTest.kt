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
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests todo assignment validation and clearing behavior. */
@de.heckenmann.visualagent.testsupport.DatabaseTest
class TodosToolAssignmentTest {
    @Test
    fun `update distinguishes omitted assignment from an explicit clear`() {
        val db = database("assignment")
        try {
            val tool = createTool(db)
            val added = tool.execute("""{"action":"add","description":"Assigned task","assignedAgentId":"agent-1"}""")
            val id = added.content.removePrefix("Added todo ")

            val cleared = tool.execute("""{"action":"update","id":"$id","assignedAgentId":null}""")

            assertTrue(cleared.success)
            assertEquals(null, db.listTodos().single().assignedAgentId)
        } finally {
            db.close()
        }
    }

    @Test
    fun `update rejects assignedAgentId referencing missing agent`() {
        val db = database("update-validation")
        try {
            val tool = createTool(db)
            val added = tool.execute("""{"action":"add","description":"Task","assignedAgentId":"agent-1"}""")
            val id = added.content.removePrefix("Added todo ")

            val invalid = tool.execute("""{"action":"update","id":"$id","assignedAgentId":"missing"}""")

            assertFalse(invalid.success)
            assertTrue(invalid.error!!.contains("must reference an existing sub-agent"))
        } finally {
            db.close()
        }
    }

    private fun database(name: String): TestPersistence {
        val path = Files.createTempDirectory("visual-agent-todos-tool-$name").resolve("todos-tool.db")
        return KnowledgeDbTestFactory.create(path.toString())
    }

    private fun createTool(db: TestPersistence): TodosTool {
        val manager = mockk<AgentManager>()
        every { manager.getSubAgent("missing") } returns null
        every { manager.getSubAgent("agent-1") } returns SubAgent(id = "agent-1", name = "Coder", role = "Implementation")
        every { manager.todoManager } returns TodoManager(db, TodoEventBus())
        return todosToolWithoutScheduling(db, manager)
    }
}
