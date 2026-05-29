package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.ui.FxmlLoader
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import java.util.UUID

class SubAgentsPanel : Region() {

    @FXML
    private lateinit var rootVBox: VBox

    @FXML
    private lateinit var titleLabel: Label

    @FXML
    private lateinit var btnAddAgent: Button

    @FXML
    private lateinit var agentsContainer: VBox

    private val agentsList = mutableListOf<SubAgentCardView>()

    // Callback for creating new agents (UI -> backend)
    var onCreateAgent: ((name: String, role: String, template: String) -> Unit)? = null

    init {
        val root = FxmlLoader.load(this, "sub-agents-panel.fxml")
        children.add(root as Region)
        setupUI()
        createDefaultAgents()
    }

    fun setAgents(agents: List<SubAgent>) {
        agentsContainer.children.clear()
        agentsList.clear()
        agents.forEach { addAgent(it) }
    }

    private fun setupUI() {
        styleClass.add("subagents-panel")
        titleLabel.text = "SubAgents"

        // Add button behavior: open dialog and delegate creation to callback or create locally
        btnAddAgent.text = "Add Agent"
        btnAddAgent.setOnAction {
            AgentDetailsDialog.showFor(null) { name, role, template ->
                if (onCreateAgent != null) {
                    onCreateAgent!!.invoke(name, role, template)
                } else {
                    val id = UUID.randomUUID().toString().take(8)
                    val newAgent = SubAgent.fromTemplate(id, name, role, template)
                    addAgent(newAgent)
                }
            }
        }
    }

    private fun createDefaultAgents() {
        addAgent(SubAgent("1", "Researcher", "Web research and information gathering", AgentStatus.IDLE))
        addAgent(SubAgent("2", "Coder", "Code implementation and review", AgentStatus.IDLE))
        addAgent(SubAgent("3", "Documenter", "Documentation writing", AgentStatus.IDLE))
    }

    // Optional external callback for agent actions (UI -> backend wiring)
    var agentActionCallback: ((action: String, agentId: String) -> Unit)? = null

    fun addAgent(agent: SubAgent) {
        val agentView = SubAgentCardView(agent)

        // Configure callbacks: delegate to agentActionCallback if present, otherwise perform local UI-only behavior
        agentView.onConfigure = { a ->
            AgentDetailsDialog.showFor(a) { name, role, template ->
                a.name = name
                a.role = role
                a.config = AgentConfig.fromTemplate(template)
                agentView.refreshDisplay()
                // propagate to backend if available
                agentActionCallback?.invoke("update", a.id)
            }
        }

        agentView.onRun = { a ->
            agentActionCallback?.invoke("run", a.id) ?: println("[UI] Run agent ${a.id}")
        }

        agentView.onLogs = { a ->
            // show logs dialog
            AgentLogsDialog.showFor(a)
        }

        agentView.onDelete = { a ->
            // confirmation before deleting
            val alert = javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION)
            alert.title = "Delete Agent"
            alert.headerText = "Delete agent ${a.name}?"
            alert.contentText = "This will remove the agent from the UI and delete persisted data if connected to backend."
            val res = alert.showAndWait()
            if (res.isPresent && res.get() == javafx.scene.control.ButtonType.OK) {
                // remove from UI
                agentsContainer.children.remove(agentView)
                agentsList.remove(agentView)
                agentActionCallback?.invoke("delete", a.id)
            }
        }

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
