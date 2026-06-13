package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.ui.FxmlLoader
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Represents SubAgentsPanel.
 */
@Component
@Lazy
class SubAgentsPanel : Region() {
    @FXML
    private lateinit var rootVBox: VBox

    @FXML
    private lateinit var titleLabel: Label

    @FXML
    private lateinit var btnAddAgent: Button

    @FXML
    private lateinit var agentsContainer: VBox

    @FXML
    private lateinit var agentCountLabel: Label

    @FXML
    private lateinit var activeJobsLabel: Label

    private val agentsList = mutableListOf<SubAgentCardView>()

    // Callback for creating new agents (UI -> backend)
    var onCreateAgent: ((name: String, role: String, template: String) -> Unit)? = null

    init {
        val root = FxmlLoader.load(this, "sub-agents-panel.fxml")
        children.add(root as Region)
        setupUI()
        createDefaultAgents()
    }

    /**
     * Executes setAgents.
     */
    fun setAgents(
        agents: List<SubAgent>,
        activeJobsByAgentId: Map<String, Int> = emptyMap(),
    ) {
        agentsContainer.children.clear()
        agentsList.clear()
        agents.forEach { agent -> addAgent(agent, activeJobsByAgentId[agent.id] ?: 0) }
        updateSummary()
    }

    private fun setupUI() {
        styleClass.add("subagents-panel")
        // Add button behavior: open dialog and delegate creation to callback or create locally
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

    /**
     * Executes addAgent.
     */
    fun addAgent(
        agent: SubAgent,
        activeJobCount: Int = 0,
    ) {
        val agentView = SubAgentCardView(agent, activeJobCount)

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
                updateSummary()
                agentActionCallback?.invoke("delete", a.id)
            }
        }

        agentsList.add(agentView)
        agentsContainer.children.add(agentView as Node)
        updateSummary()
    }

    /**
     * Executes updateAgentStatus.
     */
    fun updateAgentStatus(
        agentId: String,
        status: AgentStatus,
        task: String? = null,
        activeJobCount: Int = 0,
    ) {
        agentsList.find { it.agent.id == agentId }?.updateStatus(status, task, activeJobCount)
        updateSummary()
    }

    private fun updateSummary() {
        val count = agentsList.size
        val activeJobs = agentsList.sumOf(SubAgentCardView::activeJobCount)
        agentCountLabel.text = if (count == 1) "1 agent" else "$count agents"
        activeJobsLabel.text = if (activeJobs == 1) "1 active job" else "$activeJobs active jobs"
    }
}

/**
 * Represents SubAgentView.
 */
class SubAgentView(
    val agent: SubAgent,
) : Region() {
    @FXML
    private lateinit var root: VBox

    @FXML
    private lateinit var nameLabel: Label

    @FXML
    private lateinit var roleLabel: Label

    @FXML
    private lateinit var jobsLabel: Label

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
        jobsLabel.text = "Jobs: 0"

        taskLabel.isWrapText = true

        val content = root.children.first()
        content.styleClass.add("agent-content")
    }

    private fun updateStatusIndicator(status: AgentStatus) {
        statusIndicator.styleClass.removeAll("agent-status-idle", "agent-status-busy", "agent-status-offline")
        val statusClass =
            when (status) {
                AgentStatus.IDLE -> "agent-status-idle"
                AgentStatus.BUSY -> "agent-status-busy"
                AgentStatus.OFFLINE -> "agent-status-offline"
            }
        statusIndicator.styleClass.add(statusClass)
    }

    /**
     * Executes updateStatus.
     */
    fun updateStatus(
        status: AgentStatus,
        task: String? = null,
        activeJobCount: Int = 0,
    ) {
        agent.status = status
        agent.currentTask = task
        updateStatusIndicator(status)
        jobsLabel.text = "Jobs: ${activeJobCount.coerceAtLeast(0)}"
        taskLabel.text = task ?: ""
    }
}
