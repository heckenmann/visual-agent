package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.protocol.OnboardingAgent
import de.heckenmann.visualagent.protocol.OnboardingAgentCreationResult
import org.springframework.stereotype.Component

/** Bridges onboarding to the normal main-model and persisted sub-agent lifecycle. */
@Component
class OnboardingAgentService(
    private val agentManager: AgentManager,
) {
    /** Returns safe summaries of all currently persisted sub-agents. */
    fun agents(): List<OnboardingAgent> = agentManager.getSubAgents().map { OnboardingAgent(it.id, it.name, it.role) }

    /** Sends the user's request through the normal conversation and tool execution flow. */
    suspend fun createAgent(description: String): OnboardingAgentCreationResult {
        require(description.isNotBlank()) { "Describe the sub-agent before asking the model to create it." }
        val message = agentManager.sendMessage(description.trim())
        return OnboardingAgentCreationResult(message, agents())
    }
}
