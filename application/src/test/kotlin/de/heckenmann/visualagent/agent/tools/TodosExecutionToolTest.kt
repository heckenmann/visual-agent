package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.TodoToolPort
import de.heckenmann.visualagent.agent.tools.api.ToolTodo
import kotlin.test.Test
import kotlin.test.assertTrue

/** Tests explicit todo execution actions exposed to the main agent. */
class TodosExecutionToolTest {
    @Test
    fun `execution actions delegate through the todo port`() {
        val port = FakeTodoPort()
        val tool =
            de.heckenmann.visualagent.agent.tools
                .TodosTool(port)

        assertTrue(tool.execute("""{"action":"start","id":"todo-1"}""").content.contains("Started todo todo-1"))
        assertTrue(tool.execute("""{"action":"start-all"}""").content.contains("Started 1 todos"))
        assertTrue(tool.execute("""{"action":"stop","id":"todo-1"}""").content.contains("Stopped todo todo-1"))
        assertTrue(tool.execute("""{"action":"stop-all"}""").content.contains("Stopped 1 todos"))
    }
}

private class FakeTodoPort : TodoToolPort {
    override fun list(): List<ToolTodo> = listOf(ToolTodo("todo-1", "Task", "PENDING", 0, "agent-1"))

    override fun agentExists(agentId: String): Boolean = agentId == "agent-1"

    override fun add(
        description: String,
        assignedAgentId: String,
    ): String = "todo-1"

    override fun update(
        id: String,
        description: String?,
        assignedAgentId: String?,
        status: String?,
    ) = Unit

    override fun setStatus(
        id: String,
        status: String,
    ): Boolean = true

    override fun start(id: String): Boolean = true

    override fun startAll(): Int = 1

    override fun stop(id: String): Boolean = true

    override fun stopAll(): Int = 1

    override fun remove(id: String): Boolean = true

    override fun moveToPosition(
        id: String,
        position: Int,
    ): Boolean = true

    override fun result(id: String): String? = null
}
