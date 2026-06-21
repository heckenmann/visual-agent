package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.agent.SubAgent
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox

/**
 * Displays an agent's conversation history and current execution status.
 *
 * Use cases: UC-0000050.
 */
class AgentLogsDialog {
    companion object {
        /**
         * Opens a read-only activity view for [agent].
         */
        fun showFor(agent: SubAgent) {
            val dialog =
                Dialog<Unit>().apply {
                    title = "${agent.name} Activity"
                    headerText = "Conversation and execution history"
                    dialogPane.buttonTypes.add(ButtonType.CLOSE)
                    dialogPane.styleClass.add("agent-logs-dialog")
                }
            val history =
                TextArea(renderHistory(agent)).apply {
                    isEditable = false
                    isWrapText = true
                    styleClass.add("agent-log-content")
                    VBox.setVgrow(this, Priority.ALWAYS)
                }
            val summary =
                HBox(
                    Label(
                        agent.status.name
                            .lowercase()
                            .replaceFirstChar(Char::uppercase),
                    ).apply { styleClass.addAll("summary-pill", "summary-pill-accent") },
                    Label(agent.role).apply { styleClass.add("field-help") },
                    Region().apply { HBox.setHgrow(this, Priority.ALWAYS) },
                    Label("${agent.chatHistory.size} messages").apply { styleClass.add("field-help") },
                ).apply { styleClass.add("agent-log-summary") }

            dialog.dialogPane.content =
                VBox(12.0, summary, history).apply {
                    styleClass.add("dialog-form")
                    prefWidth = 720.0
                    prefHeight = 480.0
                }
            dialog.showAndWait()
        }

        private fun renderHistory(agent: SubAgent): String =
            if (agent.chatHistory.isEmpty()) {
                "No activity has been recorded for this agent yet."
            } else {
                agent.chatHistory.joinToString("\n\n") { message ->
                    "${message.role.uppercase()}\n${message.content}"
                }
            }
    }
}
