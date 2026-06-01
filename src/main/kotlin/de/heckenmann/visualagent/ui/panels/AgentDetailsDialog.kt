package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.agent.SubAgent
import javafx.geometry.Insets
import javafx.scene.control.ButtonBar
import javafx.scene.control.ButtonType
import javafx.scene.control.ChoiceBox
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.GridPane

/**
 * Lightweight dialog for creating/editing SubAgents.
 * Uses a programmatic layout to avoid extra FXML parsing complexity.
 */
class AgentDetailsDialog {
    companion object {
        /**
         * Executes showFor.
         */
        fun showFor(
            agent: SubAgent? = null,
            onSave: ((String, String, String) -> Unit)? = null,
        ) {
            val dialog = Dialog<Unit>()
            dialog.title = if (agent == null) "Create Agent" else "Edit Agent"
            val okButtonType = ButtonType("Save", ButtonBar.ButtonData.OK_DONE)
            dialog.dialogPane.buttonTypes.addAll(okButtonType, ButtonType.CANCEL)

            val grid = GridPane()
            grid.hgap = 10.0
            grid.vgap = 10.0
            grid.padding = Insets(10.0)

            val nameField = TextField(agent?.name ?: "")
            val roleField = TextField(agent?.role ?: "")
            val templateChoice = ChoiceBox<String>()
            templateChoice.items.addAll("researcher", "coder", "documenter", "reviewer", "tester")
            templateChoice.selectionModel.select(0)

            grid.add(Label("Name:"), 0, 0)
            grid.add(nameField, 1, 0)
            grid.add(Label("Role:"), 0, 1)
            grid.add(roleField, 1, 1)
            grid.add(Label("Template:"), 0, 2)
            grid.add(templateChoice, 1, 2)

            dialog.dialogPane.content = grid

            dialog.setResultConverter { dialogButton ->
                if (dialogButton === okButtonType) {
                    onSave?.invoke(nameField.text, roleField.text, templateChoice.selectionModel.selectedItem)
                }
                null
            }

            dialog.showAndWait()
        }
    }
}
