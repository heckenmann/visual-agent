package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.tools.api.TodoToolPort
import de.heckenmann.visualagent.testsupport.TestPersistence

/** Isolates persisted todo CRUD tests from the autonomous execution coordinator. */
internal fun todosToolWithoutScheduling(
    db: TestPersistence,
    manager: AgentManager,
): TodosTool {
    val adapter = TodoToolPortAdapter(db, db, { manager.todoManager }, { manager })
    val port =
        object : TodoToolPort by adapter {
            override fun start(id: String): Boolean = true
        }
    return TodosTool(port)
}
