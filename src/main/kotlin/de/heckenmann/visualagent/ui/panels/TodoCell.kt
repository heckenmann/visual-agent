package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoStatus
import javafx.beans.property.SimpleBooleanProperty
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid
import org.kordamp.ikonli.javafx.FontIcon

/**
 * Renders one todo row with status controls, metadata, and row actions.
 *
 * @property onStatusChanged Callback invoked when the completion checkbox changes
 * @property onRemove Callback invoked when the delete action is requested
 */
internal class TodoCell(
    private val onStatusChanged: (Todo, Boolean) -> Unit,
    private val onRemove: (Todo) -> Unit,
) : ListCell<Todo>() {
    private val checkbox = CheckBox()
    private val descriptionLabel = Label()
    private val priorityBadge = Label()
    private val statusBadge = Label()
    private val agentBadge = Label()
    private val deleteButton = Button()
    private val titleRow = HBox(10.0)
    private val metaRow = HBox(8.0)
    private val content = VBox(5.0)
    private val spacer = Region()
    private val root = HBox(10.0)
    private val completedProperty = SimpleBooleanProperty(false)

    init {
        styleClass.add("todo-list-cell")
        HBox.setHgrow(content, Priority.ALWAYS)
        HBox.setHgrow(spacer, Priority.ALWAYS)
        root.alignment = Pos.TOP_LEFT
        root.styleClass.add("todo-row")
        descriptionLabel.isWrapText = true
        descriptionLabel.styleClass.add("todo-description")
        priorityBadge.styleClass.add("badge")
        statusBadge.styleClass.addAll("badge", "todo-status-badge")
        agentBadge.styleClass.addAll("badge", "todo-agent-badge")
        deleteButton.styleClass.addAll("button-icon", "todo-delete-button")
        deleteButton.graphic = FontIcon(FontAwesomeSolid.TRASH_ALT)
        deleteButton.tooltip = Tooltip("Delete todo")
        deleteButton.isFocusTraversable = false
        titleRow.alignment = Pos.TOP_LEFT
        titleRow.children.addAll(descriptionLabel, spacer, deleteButton)
        metaRow.alignment = Pos.CENTER_LEFT
        metaRow.children.addAll(priorityBadge, statusBadge, agentBadge)
        content.children.addAll(titleRow, metaRow)
        root.children.addAll(checkbox, content)
        completedProperty.addListener { _, _, completed ->
            item?.let { todo -> onStatusChanged(todo, completed) }
            updateCompletedStyle(completed)
        }
        checkbox.selectedProperty().bindBidirectional(completedProperty)
    }

    override fun updateItem(
        todo: Todo?,
        empty: Boolean,
    ) {
        super.updateItem(todo, empty)
        if (empty || todo == null) {
            graphic = null
            return
        }
        completedProperty.set(todo.status == TodoStatus.COMPLETED)
        descriptionLabel.text = todo.description
        priorityBadge.text = todo.priority.name
        priorityBadge.styleClass.setAll("badge", "badge-${todo.priority.name.lowercase()}")
        statusBadge.text = todo.status.name.replace('_', ' ')
        statusBadge.styleClass.setAll("badge", "todo-status-badge", "todo-status-${todo.status.name.lowercase().replace('_', '-')}")
        agentBadge.text = todo.assignedAgentId?.let { "Agent $it" } ?: "Unassigned"
        agentBadge.isVisible = todo.assignedAgentId != null
        agentBadge.isManaged = todo.assignedAgentId != null
        deleteButton.setOnAction { onRemove(todo) }
        updateCompletedStyle(todo.status == TodoStatus.COMPLETED)
        graphic = root
    }

    private fun updateCompletedStyle(completed: Boolean) {
        descriptionLabel.styleClass.remove("todo-completed")
        if (completed) descriptionLabel.styleClass.add("todo-completed")
    }
}
