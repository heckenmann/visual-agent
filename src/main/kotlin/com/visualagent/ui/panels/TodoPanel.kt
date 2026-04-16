package com.visualagent.ui.panels

import com.visualagent.todo.Todo
import com.visualagent.todo.TodoPriority
import com.visualagent.todo.TodoStatus
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.scene.text.FontWeight

class TodoPanel : Region() {

    private val rootBorderPane = BorderPane()
    private val todoListView = ListView<TodoView>()
    private val items = mutableListOf<TodoView>()

    init {
        setupUI()
        createSampleTodos()
    }

    private fun setupUI() {
        styleClass.add("todo-panel")
        style = "-fx-background-color: #2d2d2d;"

        val titleLabel = Label("Todos")
        titleLabel.font = Font.font("System", FontWeight.BOLD, 16.0)
        titleLabel.style = "-fx-text-fill: #e0e0e0; -fx-padding: 8px;"

        todoListView.style = "-fx-background: transparent;"

        rootBorderPane.top = titleLabel
        rootBorderPane.center = todoListView

        children.add(rootBorderPane)
        VBox.setVgrow(todoListView, Priority.ALWAYS)
    }

    private fun createSampleTodos() {
        addTodo(Todo("1", "Set up project structure", TodoStatus.PENDING, TodoPriority.HIGH))
        addTodo(Todo("2", "Create database schema", TodoStatus.PENDING, TodoPriority.MEDIUM))
        addTodo(Todo("3", "Implement chat interface", TodoStatus.IN_PROGRESS, TodoPriority.HIGH))
    }

    fun addTodo(todo: Todo) {
        val todoView = TodoView(todo)
        items.add(todoView)
        todoListView.items.add(todoView)
    }

    fun updateTodoStatus(todoId: String, status: TodoStatus) {
        items.find { it.todo.id == todoId }?.updateStatus(status)
    }

    fun removeTodo(todoId: String) {
        val todoView = items.find { it.todo.id == todoId }
        if (todoView != null) {
            items.remove(todoView)
            todoListView.items.remove(todoView)
        }
    }
}

class TodoView(val todo: Todo) : Region() {

    private val checkbox = CheckBox()
    private val descriptionLabel = Label()
    private val priorityLabel = Label()

    init {
        setupUI()
    }

    private fun setupUI() {
        styleClass.add("todo-view")
        style = "-fx-background-color: #3d3d3d; -fx-padding: 10px; -fx-background-radius: 4px;"

        checkbox.isSelected = todo.status == TodoStatus.COMPLETED
        checkbox.style = "-fx-text-fill: #e0e0e0;"

        descriptionLabel.text = todo.description
        descriptionLabel.font = Font.font("System", 14.0)
        descriptionLabel.style = "-fx-text-fill: #e0e0e0;"

        priorityLabel.text = todo.priority.name
        priorityLabel.font = Font.font("System", 10.0)
        priorityLabel.style = "-fx-text-fill: ${getPriorityColor(todo.priority)};"

        val content = VBox(4.0, descriptionLabel, priorityLabel)
        content.style = "-fx-padding: 4px;"

        val mainBox = VBox(8.0, checkbox, content)

        children.add(mainBox)
    }

    private fun getPriorityColor(priority: TodoPriority): String {
        return when (priority) {
            TodoPriority.URGENT -> "#f44336"
            TodoPriority.HIGH -> "#ff9800"
            TodoPriority.MEDIUM -> "#4caf50"
            TodoPriority.LOW -> "#2196f3"
        }
    }

    fun updateStatus(status: TodoStatus) {
        todo.status = status
        checkbox.isSelected = status == TodoStatus.COMPLETED
        descriptionLabel.style = if (status == TodoStatus.COMPLETED) {
            "-fx-text-fill: #808080; -fx-strikethrough: true;"
        } else {
            "-fx-text-fill: #e0e0e0;"
        }
    }
}
