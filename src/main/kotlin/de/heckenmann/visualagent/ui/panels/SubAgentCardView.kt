package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.agent.AgentStatus
import de.heckenmann.visualagent.agent.SubAgent
import de.heckenmann.visualagent.ui.FxmlLoader
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.Region
import javafx.scene.layout.VBox

/**
 * Rich SubAgent card with quick actions. UI-only; action handlers are provided by the parent panel.
 */
class SubAgentCardView(
    val agent: SubAgent,
    private var activeJobCount: Int = 0,
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

    @FXML
    private lateinit var btnConfigure: Button

    @FXML
    private lateinit var btnRun: Button

    @FXML
    private lateinit var btnLogs: Button

    @FXML
    private lateinit var btnDelete: Button

    // Callbacks set by parent
    var onConfigure: ((SubAgent) -> Unit)? = null
    var onRun: ((SubAgent) -> Unit)? = null
    var onLogs: ((SubAgent) -> Unit)? = null
    var onDelete: ((SubAgent) -> Unit)? = null

    init {
        val rootNode = FxmlLoader.load(this, "agent-card.fxml")
        children.add(rootNode as Region)
        setupUI()
    }

    private fun setupUI() {
        nameLabel.text = agent.name
        roleLabel.text = agent.role
        taskLabel.isWrapText = true
        updateStatus(agent.status, agent.currentTask, activeJobCount)

        btnConfigure.setOnAction { onConfigure?.invoke(agent) }
        btnRun.setOnAction { onRun?.invoke(agent) }
        btnLogs.setOnAction { onLogs?.invoke(agent) }
        btnDelete.setOnAction { onDelete?.invoke(agent) }
    }

    /**
     * Executes updateStatus.
     */
    fun updateStatus(
        status: AgentStatus,
        task: String?,
        activeJobCount: Int = this.activeJobCount,
    ) {
        agent.status = status
        agent.currentTask = task
        this.activeJobCount = activeJobCount.coerceAtLeast(0)
        statusIndicator.text = status.name
        jobsLabel.text = "Jobs: ${this.activeJobCount}"
        taskLabel.text = task ?: ""
    }

    /**
     * Executes refreshDisplay.
     */
    fun refreshDisplay() {
        nameLabel.text = agent.name
        roleLabel.text = agent.role
        taskLabel.text = agent.currentTask ?: ""
        statusIndicator.text = agent.status.name
        jobsLabel.text = "Jobs: $activeJobCount"
    }
}
