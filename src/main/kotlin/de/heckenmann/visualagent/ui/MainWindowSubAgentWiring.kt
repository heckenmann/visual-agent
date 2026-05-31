package de.heckenmann.visualagent.ui

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.ui.panels.ChatPanel
import de.heckenmann.visualagent.ui.panels.SubAgentsPanel
import javafx.application.Platform

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
    /**
     * Registers sub-agent actions and initializes the panel from persisted state.
     */
    fun register() {
        subAgentsPanel.agentActionCallback = { action, agentId -> handleAgentAction(action, agentId) }
        subAgentsPanel.onCreateAgent = { name, role, template -> createAgent(name, role, template) }
        subAgentsPanel.setAgents(agentManager.getSubAgents())
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
            println("[MainWindow] update: agent not found in manager: $agentId")
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
            if (!ok) println("[MainWindow] Failed to assign todo ${pending.id} to agent $agentId")
        } else {
            println("[MainWindow] No pending todos to run")
        }
    }

    private fun createAgent(
        name: String,
        role: String,
        template: String,
    ) {
        val created = agentManager.createAgent(name, role, template)
        Platform.runLater {
            subAgentsPanel.addAgent(created)
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
                subAgentsPanel.updateAgentStatus(agentId, agent.status, agent.currentTask)
            }
            updateAgentCountUi()
        }
    }
}
