package de.heckenmann.visualagent.ui

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.tools.ToolCallEvent
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.ui.panels.ChatPanel
import javafx.application.Platform

/**
 * Mirrors tool and todo events into the main conversation UI.
 *
 * @property agentManager Main application orchestrator
 * @property toolEventBus Tool event source
 * @property chatPanel Conversation UI panel
 * @property chatWiring Chat callback wiring that owns fallback tool previews
 * @property refreshTodoSummary Refresh callback for todo counters
 */
internal class MainWindowToolWiring(
    private val agentManager: AgentManager,
    private val toolEventBus: ToolEventBus,
    private val chatPanel: ChatPanel,
    private val chatWiring: MainWindowChatWiring,
    private val refreshTodoSummary: () -> Unit,
) {
    private var activeToolCallCount: Int = 0

    /**
     * Registers tool-call and todo-change listeners.
     *
     * @return Close handle that unregisters all listeners
     */
    fun register(): AutoCloseable {
        val toolListener =
            toolEventBus.addListener { event ->
                Platform.runLater { handleToolEvent(event) }
            }
        val todoListener =
            agentManager.todoManager.addListener {
                Platform.runLater { refreshTodoSummary() }
            }
        return AutoCloseable {
            toolListener.close()
            todoListener.close()
        }
    }

    private fun handleToolEvent(event: ToolCallEvent) {
        when (event.phase) {
            ToolCallPhase.STARTED -> {
                activeToolCallCount += 1
                chatPanel.updateToolActivity(activeToolCallCount, event.toolId)
            }
            ToolCallPhase.FINISHED -> {
                activeToolCallCount = (activeToolCallCount - 1).coerceAtLeast(0)
                chatPanel.updateToolActivity(activeToolCallCount, event.toolId)
                chatWiring.updateToolPreview(buildToolResultPreview(event))
                agentManager.recordToolCall(event)
                chatPanel.addToolCallEvent(event)
                if (event.toolId == "todos") {
                    refreshTodoSummary()
                }
            }
        }
    }

    private fun buildToolResultPreview(event: ToolCallEvent): String {
        val status = if (event.result.success) "ok" else "error"
        val line =
            event.result.content
                .lineSequence()
                .firstOrNull()
                ?.trim()
                .orEmpty()
        return when {
            line.isNotBlank() -> "Tool ${event.toolId} ($status): $line"
            !event.result.error.isNullOrBlank() -> "Tool ${event.toolId} ($status): ${event.result.error}"
            else -> "Tool ${event.toolId} ($status)."
        }.take(240)
    }
}
