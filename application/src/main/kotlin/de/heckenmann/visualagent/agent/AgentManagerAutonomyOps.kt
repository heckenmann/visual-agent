package de.heckenmann.visualagent.agent

/**
 * Handles autonomous task processing for [AgentManager].
 *
 * Assignment and execution are now automatic; the main agent only manages
 * sub-agent definitions and todo state.
 */
internal class AgentManagerAutonomyOps(
    private val owner: AgentManager,
) {
    fun seedUxTodos() = owner.autonomousCoordinator.seedUxTodos()

    fun startAutonomousProcessing(seed: Boolean = true) = owner.autonomousCoordinator.startAutonomousProcessing(seed)

    fun startAutonomousMode(goal: String) = owner.autonomousCoordinator.startAutonomousMode(goal)

    fun startTodo(todoId: String): Boolean = owner.autonomousCoordinator.startTodo(todoId)

    fun startAllTodos(): Int = owner.autonomousCoordinator.startAllTodos()

    fun stopTodo(todoId: String): Boolean = owner.autonomousCoordinator.stopTodo(todoId)

    fun stopAllTodos(): Int = owner.autonomousCoordinator.stopAllTodos()
}
