package de.heckenmann.visualagent.ui

import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.ui.panels.ChatPanel
import de.heckenmann.visualagent.ui.panels.SubAgentsPanel
import javafx.application.Platform
import mu.KotlinLogging

/**
 * Wires sub-agent UI actions and runtime notifications.
 *
 * @property agentManager Main application orchestrator
 * @property subAgentsPanel Sub-agent list panel
 * @property chatPanel Conversation panel used for agent notifications
 * @property updateAgentCountUi Refresh callback for the shell agent count
 */
internal class MainWindowSubAgentWiring(
    private val agentManager: AgentManager,
    private val subAgentsPanel: SubAgentsPanel,
    private val chatPanel: ChatPanel,
    private val updateAgentCountUi: () -> Unit,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Registers sub-agent actions and initializes the panel from persisted state.
     */
    fun register() {
        subAgentsPanel.agentActionCallback = { action, agentId -> handleAgentAction(action, agentId) }
        subAgentsPanel.onCreateAgent = { name, role, config -> createAgent(name, role, config) }
        val agents = agentManager.getSubAgents()
        subAgentsPanel.setAgents(
            agents,
            agents.associate { agent -> agent.id to agentManager.getActiveJobCount(agent.id) },
        )
        AgentManager.setAgentCallback { agentId, message -> handleAgentCallback(agentId, message) }
    }

    private fun handleAgentAction(
        action: String,
        agentId: String,
    ) {
        when (action) {
            "update" -> updateAgent(agentId)
            "delete" -> deleteAgent(agentId)
            "run" -> runAgent(agentId)
        }
    }

    private fun updateAgent(agentId: String) {
        val uiAgent = agentManager.getSubAgent(agentId)
        if (uiAgent != null) {
            agentManager.updateAgent(agentId, name = uiAgent.name, role = uiAgent.role, config = uiAgent.config)
            Platform.runLater { updateAgentCountUi() }
        } else {
            logger.warn { "Cannot update missing agent: $agentId" }
        }
    }

    private fun deleteAgent(agentId: String) {
        if (agentManager.deleteAgent(agentId)) {
            Platform.runLater { updateAgentCountUi() }
        }
    }

    private fun runAgent(agentId: String) {
        val pending =
            agentManager
                .getTodosFromDb()
                .firstOrNull { it.status == TodoStatus.PENDING }
        if (pending != null) {
            val ok = agentManager.assignTodoToAgent(pending.id, agentId)
            if (!ok) logger.warn { "Failed to assign todo ${pending.id} to agent $agentId" }
        } else {
            logger.info { "No pending todos available for agent run action" }
        }
    }

    private fun createAgent(
        name: String,
        role: String,
        config: AgentConfig,
    ) {
        val created = agentManager.createAgent(name, role)
        agentManager.updateAgent(created.id, config = config)
        Platform.runLater {
            subAgentsPanel.addAgent(created, agentManager.getActiveJobCount(created.id))
            updateAgentCountUi()
        }
    }

    private fun handleAgentCallback(
        agentId: String,
        message: String,
    ) {
        Platform.runLater {
            chatPanel.addAssistantMessage("[$agentId]: $message")
            val agent = agentManager.getSubAgent(agentId)
            if (agent != null) {
                subAgentsPanel.updateAgentStatus(
                    agentId,
                    agent.status,
                    agent.currentTask,
                    agentManager.getActiveJobCount(agentId),
                )
            }
            updateAgentCountUi()
        }
    }
}
