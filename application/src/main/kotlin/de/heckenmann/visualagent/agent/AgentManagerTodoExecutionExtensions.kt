package de.heckenmann.visualagent.agent

/** Starts one pending or cancelled todo through the autonomous coordinator. */
fun AgentManager.startTodo(todoId: String): Boolean = autonomyOps.startTodo(todoId)

/** Starts all pending and cancelled todos through the autonomous coordinator. */
fun AgentManager.startAllTodos(): Int = autonomyOps.startAllTodos()

/** Stops one pending or in-progress todo and cancels its worker cooperatively. */
fun AgentManager.stopTodo(todoId: String): Boolean = autonomyOps.stopTodo(todoId)

/** Stops all pending and in-progress todos and cancels their workers cooperatively. */
fun AgentManager.stopAllTodos(): Int = autonomyOps.stopAllTodos()

/** Deletes every persisted todo and publishes a single clear event. */
fun AgentManager.clearTodos() {
    todoManager.clear()
}
