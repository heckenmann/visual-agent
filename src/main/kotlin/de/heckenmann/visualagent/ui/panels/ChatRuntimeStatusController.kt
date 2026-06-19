package de.heckenmann.visualagent.ui.panels.chat

import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox

/**
 * Updates conversation runtime indicators such as tool activity and todo summary.
 *
 * @property assistantBusyContainer Container for active tool-call activity
 * @property assistantBusyLabel Label describing active tool calls
 * @property todoInfoLabel Todo summary label
 * @property responseMetaLabel Label for response timing feedback
 * @property todoSummaryTooltip Tooltip with detailed todo counters
 */
internal class ChatRuntimeStatusController(
    private val assistantBusyContainer: HBox,
    private val assistantBusyLabel: Label,
    private val todoInfoLabel: Label,
    private val responseMetaLabel: Label,
    private val todoSummaryTooltip: Tooltip,
) {
    private var activeToolCalls = 0
    private var latestToolId: String? = null

    /**
     * Updates active tool-call activity.
     */
    fun updateToolActivity(
        activeCount: Int,
        latestToolId: String?,
    ) {
        activeToolCalls = activeCount.coerceAtLeast(0)
        this.latestToolId = latestToolId
        updateRuntimeStatus()
    }

    /**
     * Updates todo summary counts.
     */
    fun updateTodoSummary(
        total: Int,
        open: Int,
        inProgress: Int,
        completed: Int,
        cancelled: Int,
    ) {
        todoInfoLabel.text = "Open $open of $total"
        todoSummaryTooltip.text = "Open: $open\nIn Progress: $inProgress\nDone: $completed\nCancelled: $cancelled\nTotal: $total"
    }

    /**
     * Shows the last completed response latency in the conversation header.
     */
    fun updateResponseMetrics(durationMillis: Long) {
        responseMetaLabel.text = "Last response ${String.format("%.2fs", durationMillis / 1000.0)}"
    }

    /**
     * Refreshes the visible runtime status row.
     */
    fun updateRuntimeStatus() {
        val active = activeToolCalls > 0
        assistantBusyContainer.isManaged = active
        assistantBusyContainer.isVisible = active
        assistantBusyLabel.text =
            when {
                activeToolCalls > 0 -> {
                    val suffix = if (latestToolId.isNullOrBlank()) "" else " · $latestToolId"
                    "Tools running ($activeToolCalls)$suffix"
                }
                else -> "Idle"
            }
    }
}
