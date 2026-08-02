package de.heckenmann.visualagent.agent

import java.util.concurrent.ConcurrentHashMap

/**
 * Provides sub-agent lifecycle operations for coordinator beans, breaking the
 * circular dependency between [AgentManager] and the coordinators.
 *
 * [AgentManager] wires the lambdas via [setSaveSubAgent], [setCreateAgent],
 * and [setNotifyAgent] during its `init` block.
 */
class SubAgentOpsProvider {
    private val subAgents = ConcurrentHashMap<String, SubAgent>()

    /**
     * Read-only view of the currently loaded sub-agents.
     */
    val allSubAgents: Map<String, SubAgent> = subAgents

    private var saveSubAgent: ((SubAgent) -> Unit)? = null
    private var createAgent: ((String, String, String) -> SubAgent)? = null
    private var notifyAgent: ((String, String) -> Unit)? = null

    /**
     * Sets the lambda for persisting a sub-agent to the database.
     */
    fun setSaveSubAgent(fn: (SubAgent) -> Unit) {
        saveSubAgent = fn
    }

    /**
     * Sets the lambda for creating a new sub-agent.
     */
    fun setCreateAgent(fn: (String, String, String) -> SubAgent) {
        createAgent = fn
    }

    /**
     * Sets the lambda for notifying a sub-agent with a status message.
     */
    fun setNotifyAgent(fn: (String, String) -> Unit) {
        notifyAgent = fn
    }

    /**
     * Adds or replaces a sub-agent in the in-memory registry.
     */
    fun putSubAgent(agent: SubAgent) {
        subAgents[agent.id] = agent
    }

    /**
     * Returns the sub-agent with the given ID, or null if not found.
     */
    fun getSubAgent(id: String): SubAgent? = subAgents[id]

    /**
     * Removes the sub-agent with the given ID and returns it, or null if not found.
     */
    fun removeSubAgent(id: String): SubAgent? = subAgents.remove(id)

    /**
     * Clears all loaded sub-agents.
     */
    fun clearSubAgents() = subAgents.clear()

    /**
     * Persists a sub-agent to the database via the configured lambda.
     */
    fun saveSubAgent(agent: SubAgent) = checkNotNull(saveSubAgent) { "saveSubAgent not wired; ensure AgentManager.init completed" }(agent)

    /**
     * Creates a new sub-agent with the given name, role, and template via the configured lambda.
     */
    fun createAgent(
        name: String,
        role: String,
        templateName: String,
    ): SubAgent = checkNotNull(createAgent) { "createAgent not wired; ensure AgentManager.init completed" }(name, role, templateName)

    /**
     * Notifies a sub-agent with a status message via the configured lambda.
     */
    fun notifyAgent(
        agentId: String,
        message: String,
    ) = checkNotNull(notifyAgent) { "notifyAgent not wired; ensure AgentManager.init completed" }(agentId, message)
}
