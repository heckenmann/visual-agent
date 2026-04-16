package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.SubAgent
import javafx.scene.control.Label
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.scene.text.FontWeight

class SubAgentsPanel : Region() {

    private val rootVBox = VBox()
    private val titleLabel = Label("SubAgents")
    private val agentsList = mutableListOf<SubAgentView>()

    init {
        setupUI()
        createDefaultAgents()
    }

    private fun setupUI() {
        styleClass.add("subagents-panel")

        titleLabel.font = Font.font("System", FontWeight.BOLD, 16.0)
        titleLabel.style = "-fx-text-fill: #e0e0e0; -fx-padding: 8px;"

        rootVBox.spacing = 8.0
        rootVBox.style = "-fx-background-color: #2d2d2d; -fx-padding: 8px;"

        rootVBox.children.add(titleLabel)

        children.add(rootVBox)
        VBox.setVgrow(this, Priority.ALWAYS)

        minWidth = 200.0
        maxWidth = 300.0
        minHeight = 300.0
        maxHeight = 600.0
    }

    private fun createDefaultAgents() {
        addAgent(SubAgent("1", "Researcher", "Web research and information gathering", AgentStatus.IDLE))
        addAgent(SubAgent("2", "Coder", "Code implementation and review", AgentStatus.IDLE))
        addAgent(SubAgent("3", "Documenter", "Documentation writing", AgentStatus.IDLE))
    }

    fun addAgent(agent: SubAgent) {
        val agentView = SubAgentView(agent)
        agentsList.add(agentView)
        rootVBox.children.add(agentView)
    }

    fun updateAgentStatus(agentId: String, status: AgentStatus, task: String? = null) {
        agentsList.find { it.agent.id == agentId }?.updateStatus(status, task)
    }
}

class SubAgentView(val agent: SubAgent) : Region() {

    private val nameLabel = Label()
    private val roleLabel = Label()
    private val statusIndicator = Region()
    private val taskLabel = Label()

    init {
        setupUI()
    }

    private fun setupUI() {
        styleClass.add("agent-view")
        style = "-fx-background-color: #3d3d3d; -fx-padding: 10px; -fx-background-radius: 4px;"

        nameLabel.text = agent.name
        nameLabel.font = Font.font("System", FontWeight.BOLD, 14.0)
        nameLabel.style = "-fx-text-fill: #ffffff;"

        roleLabel.text = agent.role
        roleLabel.font = Font.font("System", 12.0)
        roleLabel.style = "-fx-text-fill: #a0a0a0;"

        statusIndicator.style = "-fx-min-width: 10px; -fx-min-height: 10px; -fx-background-radius: 5px;"
        updateStatusIndicator(agent.status)

        taskLabel.font = Font.font("System", 10.0)
        taskLabel.style = "-fx-text-fill: #808080;"
        taskLabel.isWrapText = true

        val content = VBox(statusIndicator, nameLabel, roleLabel, taskLabel)
        content.spacing = 4.0
        content.style = "-fx-padding: 4px;"

        children.add(content)
    }

    private fun updateStatusIndicator(status: AgentStatus) {
        val color = when (status) {
            AgentStatus.IDLE -> "#4caf50"
            AgentStatus.BUSY -> "#ff9800"
            AgentStatus.OFFLINE -> "#757575"
        }
        statusIndicator.style = "-fx-min-width: 10px; -fx-min-height: 10px; -fx-background-radius: 5px; -fx-background-color: $color;"
    }

    fun updateStatus(status: AgentStatus, task: String? = null) {
        agent.status = status
        agent.currentTask = task
        updateStatusIndicator(status)
        taskLabel.text = task ?: ""
    }
}
