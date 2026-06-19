package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.agent.AgentConfig
import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
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
 * Panel that displays configured sub-agents, their workload, and quick actions.
 *
 * The panel can run in Spring-backed mode with a [ProviderCatalogService] or
 * in UI tests without one; in the latter case model/provider choices are empty.
 */
@Component
@Lazy
class SubAgentsPanel(
    private val providerCatalog: ProviderCatalogService? = null,
) : Region() {
    @FXML
    private lateinit var rootVBox: VBox

    @FXML
    private lateinit var titleLabel: Label

    @FXML
    private lateinit var btnAddAgent: Button

    @FXML
    private lateinit var emptyCreateAgentButton: Button

    @FXML
    private lateinit var agentsContainer: VBox

    @FXML
    private lateinit var agentCountLabel: Label

    @FXML
    private lateinit var activeJobsLabel: Label

    @FXML
    private lateinit var emptyState: VBox

    private val agentsList = mutableListOf<SubAgentCardView>()

    // Callback for creating new agents (UI -> backend)
    var onCreateAgent: ((name: String, role: String, config: AgentConfig) -> Unit)? = null

    init {
        val root = FxmlLoader.load(this, "sub-agents-panel.fxml")
        children.add(root as Region)
        setupUI()
        createDefaultAgents()
    }

    /**
     * Replaces the visible agent cards with persisted runtime state.
     *
     * @param agents Agents to render
     * @param activeJobsByAgentId Current active job count keyed by agent ID
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
        btnAddAgent.setOnAction { showCreateAgentDialog() }
        emptyCreateAgentButton.setOnAction { showCreateAgentDialog() }
    }

    private fun showCreateAgentDialog() {
        AgentDetailsDialog.showFor(null, selectableProviderProfiles()) { name, role, config ->
            if (onCreateAgent != null) {
                onCreateAgent!!.invoke(name, role, config)
            } else {
                val id = UUID.randomUUID().toString().take(8)
                val newAgent = SubAgent(id = id, name = name, role = role, config = config)
                addAgent(newAgent)
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
     * Adds one agent card and wires its local action callbacks.
     *
     * @param agent Agent to render
     * @param activeJobCount Current number of jobs running for this agent
     */
    fun addAgent(
        agent: SubAgent,
        activeJobCount: Int = 0,
    ) {
        val agentView = SubAgentCardView(agent, activeJobCount)

        // Configure callbacks: delegate to agentActionCallback if present, otherwise perform local UI-only behavior
        agentView.onConfigure = { a ->
            AgentDetailsDialog.showFor(a, selectableProviderProfiles()) { name, role, config ->
                a.name = name
                a.role = role
                a.config = config
                agentView.refreshDisplay()
                // propagate to backend if available
                agentActionCallback?.invoke("update", a.id)
            }
        }

        agentView.onRun = { a ->
            agentActionCallback?.invoke("run", a.id)
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
     * Updates one visible agent card after scheduler or backend state changes.
     *
     * @param agentId Agent card identifier
     * @param status New runtime status
     * @param task Optional task summary
     * @param activeJobCount Number of active jobs for this agent
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
        emptyState.isVisible = count == 0
        emptyState.isManaged = count == 0
    }

    private fun selectableProviderProfiles() =
        providerCatalog
            ?.enabledProviders()
            ?.map { profile -> profile.copy(models = providerCatalog.selectableModels(profile.id)) }
            .orEmpty()

    /**
     * Resizes the loaded FXML root to the full panel bounds.
     */
    override fun layoutChildren() {
        rootVBox.resizeRelocate(0.0, 0.0, width, height)
    }
}
