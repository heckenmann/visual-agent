package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoPriority
import de.heckenmann.visualagent.todo.TodoStatus
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.ComboBox
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.VBox
import java.util.UUID

internal data class TodoDialogResult(
    val description: String,
    val priority: TodoPriority,
)

internal fun showAddTodoDialog(): TodoDialogResult? {
    val descField = TextField()
    descField.promptText = "Describe the task..."
    descField.styleClass.add("chat-input")
    descField.prefColumnCount = 30

    val priorityCombo = priorityCombo(TodoPriority.MEDIUM)
    val dialog = todoDialog<Todo>("Add Todo", descField, priorityCombo)
    val okButton = dialog.dialogPane.lookupButton(ButtonType.OK) as Button
    okButton.disableProperty().bind(descField.textProperty().isEmpty)

    dialog.setResultConverter { buttonType ->
        if (buttonType == ButtonType.OK && descField.text.isNotBlank()) {
            Todo(
                id = UUID.randomUUID().toString(),
                description = descField.text.trim(),
                priority = priorityCombo.selectionModel.selectedItem ?: TodoPriority.MEDIUM,
                status = TodoStatus.PENDING,
            )
        } else {
            null
        }
    }

    return dialog
        .showAndWait()
        .map { TodoDialogResult(it.description, it.priority) }
        .orElse(null)
}

internal fun showEditTodoDialog(selected: Todo): TodoDialogResult? {
    val descField = TextField(selected.description)
    descField.promptText = "Describe the task..."
    descField.prefColumnCount = 30

    val priorityCombo = priorityCombo(selected.priority)
    val dialog = todoDialog<Boolean>("Edit Todo", descField, priorityCombo)
    dialog.setResultConverter { buttonType ->
        buttonType == ButtonType.OK && descField.text.isNotBlank()
    }

    return dialog
        .showAndWait()
        .filter { changed -> changed }
        .map {
            TodoDialogResult(
                descField.text.trim(),
                priorityCombo.selectionModel.selectedItem ?: TodoPriority.MEDIUM,
            )
        }.orElse(null)
}

private fun priorityCombo(selected: TodoPriority): ComboBox<TodoPriority> =
    ComboBox<TodoPriority>(FXCollections.observableArrayList(TodoPriority.entries.toList())).apply {
        selectionModel.select(selected)
    }

private fun <T> todoDialog(
    title: String,
    descField: TextField,
    priorityCombo: ComboBox<TodoPriority>,
): Dialog<T> {
    val dialog = Dialog<T>()
    dialog.title = title
    dialog.dialogPane.styleClass.add("todo-dialog")

    val content = VBox(14.0)
    content.padding = Insets(24.0)
    content.children.addAll(Label("Task Description"), descField, Label("Priority Level"), priorityCombo)
    dialog.dialogPane.content = content
    dialog.dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)
    return dialog
}
