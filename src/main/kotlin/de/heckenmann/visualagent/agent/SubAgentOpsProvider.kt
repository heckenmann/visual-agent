package de.heckenmann.visualagent.agent

import org.springframework.stereotype.Service

/**
 * Provides sub-agent lifecycle operations for coordinator beans, breaking the
 * circular dependency between [AgentManager] and the coordinators.
 *
 * [AgentManager] wires the lambdas via [setSaveSubAgent], [setCreateAgent],
 * and [setNotifyAgent] during its `init` block.
 */
@Service
class SubAgentOpsProvider {
    val subAgents = mutableMapOf<String, SubAgent>()

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
     * Persists a sub-agent to the database via the configured lambda.
     */
    fun saveSubAgent(agent: SubAgent) = saveSubAgent!!(agent)

    /**
     * Creates a new sub-agent with the given name, role, and template via the configured lambda.
     */
    fun createAgent(
        name: String,
        role: String,
        templateName: String,
    ): SubAgent = createAgent!!(name, role, templateName)

    /**
     * Notifies a sub-agent with a status message via the configured lambda.
     */
    fun notifyAgent(
        agentId: String,
        message: String,
    ) = notifyAgent!!(agentId, message)
}
