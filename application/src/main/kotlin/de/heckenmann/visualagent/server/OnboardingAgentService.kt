package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.protocol.OnboardingAgent
import de.heckenmann.visualagent.protocol.OnboardingAgentCreationAvailability
import de.heckenmann.visualagent.protocol.OnboardingAgentCreationResult
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Bridges onboarding to the normal main-model and persisted sub-agent lifecycle. */
@Component
class OnboardingAgentService(
    private val agentManager: AgentManager,
    private val providerCatalog: ProviderCatalogService,
    private val agentToolConfig: AgentToolConfigService,
    private val toolEvents: ToolEventBus,
) {
    /** Returns safe summaries of all currently persisted sub-agents. */
    fun agents(): List<OnboardingAgent> = agentManager.getSubAgents().map { OnboardingAgent(it.id, it.name, it.role) }

    /** Checks the selected model and respects the main agent's globally filtered tool set. */
    fun agentCreationAvailability(): OnboardingAgentCreationAvailability {
        if (!providerCatalog.activeModelSupportsToolCalling()) {
            return OnboardingAgentCreationAvailability(
                available = false,
                reason = "The selected model cannot call tools. Choose a model with tool calling enabled, or skip this step.",
                canSelectAnotherModel = true,
            )
        }
        val enabledTools = agentToolConfig.mainAgentTools().mapTo(mutableSetOf()) { it.value }
        val requiredTools = setOf("agent:list", "agent:create")
        val missingTools = requiredTools - enabledTools
        if (missingTools.isNotEmpty()) {
            return OnboardingAgentCreationAvailability(
                available = false,
                reason = "Enable ${missingTools.joinToString()} in the Main Agent tool settings, or skip this step.",
            )
        }
        return OnboardingAgentCreationAvailability(available = true)
    }

    /** Sends the user's request through the normal conversation and tool execution flow. */
    suspend fun createAgent(description: String): OnboardingAgentCreationResult {
        require(description.isNotBlank()) { "Describe the sub-agent before asking the model to create it." }
        val requestId = UUID.randomUUID().toString()
        val created = AtomicBoolean(false)
        val message =
            toolEvents
                .addListener { event ->
                    if (event.requestId == requestId &&
                        event.phase == ToolCallPhase.FINISHED &&
                        event.toolId == "agent:create" &&
                        event.result.success
                    ) {
                        created.set(true)
                    }
                }.use { agentManager.sendMessage(description.trim(), requestId) }
        return OnboardingAgentCreationResult(
            message = message,
            agents = agents(),
            agentCreated = created.get(),
        )
    }
}
