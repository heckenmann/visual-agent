package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.ui.FxmlLoader
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.Region
import javafx.scene.layout.VBox

class SubAgentsPanel : Region() {

    @FXML
    private lateinit var rootVBox: VBox

    @FXML
    private lateinit var titleLabel: Label

    @FXML
    private lateinit var agentsContainer: VBox

    private val agentsList = mutableListOf<SubAgentView>()

    init {
        val root = FxmlLoader.load(this, "sub-agents-panel.fxml")
        children.add(root as Region)
        setupUI()
        createDefaultAgents()
    }

    private fun setupUI() {
        styleClass.add("subagents-panel")
        titleLabel.text = "SubAgents"
    }

    private fun createDefaultAgents() {
        addAgent(SubAgent("1", "Researcher", "Web research and information gathering", AgentStatus.IDLE))
        addAgent(SubAgent("2", "Coder", "Code implementation and review", AgentStatus.IDLE))
        addAgent(SubAgent("3", "Documenter", "Documentation writing", AgentStatus.IDLE))
    }

    fun addAgent(agent: SubAgent) {
        val agentView = SubAgentView(agent)
        agentsList.add(agentView)
        agentsContainer.children.add(agentView as Node)
    }

    fun updateAgentStatus(agentId: String, status: AgentStatus, task: String? = null) {
        agentsList.find { it.agent.id == agentId }?.updateStatus(status, task)
    }
}

class SubAgentView(val agent: SubAgent) : Region() {

    @FXML
    private lateinit var root: VBox

    @FXML
    private lateinit var nameLabel: Label

    @FXML
    private lateinit var roleLabel: Label

    @FXML
    private lateinit var statusIndicator: Label

    @FXML
    private lateinit var taskLabel: Label

    init {
        val root = FxmlLoader.load(this, "sub-agent-item.fxml")
        children.add(root as Region)
        setupUI()
    }

    private fun setupUI() {
        styleClass.add("agent-view")

        nameLabel.text = agent.name
        roleLabel.text = agent.role

        statusIndicator.styleClass.addAll("agent-status-indicator", "agent-status-idle")
        updateStatusIndicator(agent.status)

        taskLabel.isWrapText = true

        val content = root.children.first()
        content.styleClass.add("agent-content")
    }

    private fun updateStatusIndicator(status: AgentStatus) {
        statusIndicator.styleClass.removeAll("agent-status-idle", "agent-status-busy", "agent-status-offline")
        val statusClass = when (status) {
            AgentStatus.IDLE -> "agent-status-idle"
            AgentStatus.BUSY -> "agent-status-busy"
            AgentStatus.OFFLINE -> "agent-status-offline"
        }
        statusIndicator.styleClass.add(statusClass)
    }

    fun updateStatus(status: AgentStatus, task: String? = null) {
        agent.status = status
        agent.currentTask = task
        updateStatusIndicator(status)
        taskLabel.text = task ?: ""
    }
}
