package de.heckenmann.visualagent.agent

/**
 * Handles autonomous task assignment and processing for [AgentManager].
 */
internal class AgentManagerAutonomyOps(
    private val owner: AgentManager,
) {
    fun assignNextTodo(): Boolean = owner.autonomousCoordinator.assignNextTodo()

    fun assignTodoToAgent(
        todoId: String,
        agentId: String,
    ): Boolean = owner.autonomousCoordinator.assignTodoToAgent(todoId, agentId)

    fun assignAllPendingTodos(): Int = owner.autonomousCoordinator.assignAllPendingTodos()

    fun seedUxTodos() = owner.autonomousCoordinator.seedUxTodos()

    fun startAutonomousProcessing(seed: Boolean = true) = owner.autonomousCoordinator.startAutonomousProcessing(seed)

    fun startAutonomousMode(goal: String) = owner.autonomousCoordinator.startAutonomousMode(goal)
}
