package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.agent.SubAgent
import javafx.scene.control.Dialog
import javafx.scene.control.TextArea
import javafx.scene.control.ButtonType
import javafx.scene.layout.VBox

/**
 * Simple dialog to show an agent's chat history/logs.
 */
class AgentLogsDialog {
    companion object {
        fun showFor(agent: SubAgent) {
            val dialog = Dialog<Unit>()
            dialog.title = "Agent Logs - ${agent.name}"
            dialog.dialogPane.buttonTypes.add(ButtonType.CLOSE)

            val ta = TextArea()
            ta.isEditable = false
            ta.isWrapText = true

            val sb = StringBuilder()
            if (agent.chatHistory.isEmpty()) {
                sb.append("(no logs)")
            } else {
                agent.chatHistory.forEach { msg ->
                    sb.append("[${msg.role}] ${msg.content}\n\n")
                }
            }
            ta.text = sb.toString()

            val box = VBox(ta)
            box.prefWidth = 600.0
            box.prefHeight = 400.0
            dialog.dialogPane.content = box
            dialog.showAndWait()
        }
    }
}
