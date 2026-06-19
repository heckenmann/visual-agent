package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoStatus
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.VBox

internal fun updateTodoEmptyState(
    todoEmptyState: VBox,
    emptyAddButton: Button,
    titleLabel: Label,
    descriptionLabel: Label,
    sourceIsEmpty: Boolean,
    filteredIsEmpty: Boolean,
) {
    todoEmptyState.isVisible = filteredIsEmpty
    todoEmptyState.isManaged = filteredIsEmpty
    emptyAddButton.isVisible = sourceIsEmpty
    emptyAddButton.isManaged = sourceIsEmpty
    if (sourceIsEmpty) {
        titleLabel.text = "No todos yet"
        descriptionLabel.text = "Create a task yourself or let an agent create one."
    } else {
        titleLabel.text = "No matching todos"
        descriptionLabel.text = "Adjust the filter to show existing tasks."
    }
}

internal fun updateTodoActionStates(
    editButton: Button,
    completeAllButton: Button,
    deleteCompletedButton: Button,
    selectedTodo: Todo?,
    allTodos: List<Todo>,
) {
    editButton.isDisable = selectedTodo == null
    completeAllButton.isDisable = allTodos.none { it.status != TodoStatus.COMPLETED }
    deleteCompletedButton.isDisable = allTodos.none { it.status == TodoStatus.COMPLETED }
}
