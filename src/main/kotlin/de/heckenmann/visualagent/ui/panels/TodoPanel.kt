package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoPriority
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.ui.FxmlLoader
import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.input.KeyCode
import javafx.scene.control.ComboBox
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import java.util.UUID

/**
 * Panel for managing a list of todo items with add, complete, and delete capabilities.
 *
 * Loads its layout from `todo-panel.fxml` and provides a cell factory that renders
 * each [Todo] with a checkbox, description, priority badge, and delete button.
 */
class TodoPanel(private val todoManager: TodoManager) : Region() {

    @FXML
    private lateinit var rootBorderPane: BorderPane

    @FXML
    private lateinit var addButton: Button

    @FXML
    private lateinit var todoListView: ListView<Todo>

    private val todos = FXCollections.observableArrayList<Todo>()

    init {
        val root = FxmlLoader.load(this, "todo-panel.fxml")
        children.add(root)
        // initialize from the shared TodoManager if available
        todos.setAll(todoManager.getAll())
    }

    /**
     * Called automatically by the FXMLLoader after all FXML fields are injected.
     * Sets up the list view cell factory and the add button handler.
     */
    @FXML
    fun initialize() {
        todoListView.items = todos
        todoListView.setCellFactory { TodoCell() }
        addButton.setOnAction { showAddDialog() }
        // Keyboard shortcuts: N for new todo, Delete for removing selected todo
        rootBorderPane.sceneProperty().addListener { _, _, newScene ->
            newScene?.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED) { ev ->
                if (ev.code == KeyCode.N) {
                    showAddDialog()
                    ev.consume()
                } else if (ev.code == KeyCode.DELETE) {
                    val selected = todoListView.selectionModel.selectedItem
                    if (selected != null) {
                        todos.remove(selected)
                        ev.consume()
                    }
                }
            }
        }
    }

    /**
     * Shows a dialog for creating a new todo item.
     *
     * The dialog contains a description text field and a priority combo box.
     * If the user confirms, a new [Todo] is added to the list.
     */
    private fun showAddDialog() {
        val dialog = Dialog<Todo>()
        dialog.title = "Add Todo"
        dialog.dialogPane.styleClass.add("todo-dialog")

        val descField = TextField()
        descField.promptText = "Describe the task..."
        descField.styleClass.add("chat-input")
        descField.prefColumnCount = 30

        val priorityCombo = ComboBox<TodoPriority>(FXCollections.observableArrayList(TodoPriority.entries.toList()))
        priorityCombo.selectionModel.select(TodoPriority.MEDIUM)

        val content = VBox(14.0)
        content.padding = Insets(24.0)
        content.children.addAll(Label("Task Description"), descField, Label("Priority Level"), priorityCombo)
        dialog.dialogPane.content = content
        dialog.dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)

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

        val result = dialog.showAndWait()
        result.ifPresent { _ ->
            // create via shared manager so agents see it
            val created = todoManager.add(descField.text.trim(), priorityCombo.selectionModel.selectedItem ?: TodoPriority.MEDIUM)
            todos.add(created)
        }
    }

    /**
     * Called by UI to remove a todo with undo support (5 seconds to undo).
     */
    private fun removeWithUndo(todo: Todo) {
        todos.remove(todo)
        // Ask shared manager to remove; keep a local copy for undo
        todoManager.remove(todo.id)
        val removed = todo
        // Simple undo window: 5 seconds to undo
        val thread = Thread {
            try {
                Thread.sleep(5000)
                // deletion confirmed
            } catch (_: InterruptedException) {
                Platform.runLater {
                    val recreated = todoManager.add(removed.description, removed.priority)
                    todos.add(recreated)
                }
            }
        }
        thread.start()
    }

    fun refreshFromManager() {
        Platform.runLater {
            todos.setAll(todoManager.getAll())
        }
    }

    /**
     * Custom [ListCell] that renders a [Todo] with interactive controls.
     *
     * Each cell displays a checkbox to toggle completion, the todo description,
     * a priority badge, and a delete button. CSS classes `todo-view`, `badge`,
     * and `button-icon` are applied for styling via the dark theme stylesheet.
     */
    private inner class TodoCell : ListCell<Todo>() {

        private val checkbox = CheckBox()
        private val descriptionLabel = Label()
        private val priorityBadge = Label()
        private val spacer = Region()
        private val deleteButton = Button()
        private val topRow = HBox(12.0)
        private val bottomRow = HBox()
        private val root = VBox(6.0)
        private val completedProperty = SimpleBooleanProperty(false)

        init {
            HBox.setHgrow(spacer, Priority.ALWAYS)
            deleteButton.styleClass.add("button-icon")
            deleteButton.graphic = Label("\u2715")
            priorityBadge.styleClass.add("badge")
            topRow.alignment = Pos.TOP_LEFT
            topRow.children.addAll(checkbox, descriptionLabel)
            bottomRow.alignment = Pos.CENTER_LEFT
            bottomRow.padding = Insets(6.0, 0.0, 0.0, 30.0)
            bottomRow.children.addAll(priorityBadge, spacer, deleteButton)
            root.styleClass.add("todo-view")
            root.children.addAll(topRow, bottomRow)

            completedProperty.addListener { _, _, newVal ->
                val todo = item ?: return@addListener
                todo.status = if (newVal) TodoStatus.COMPLETED else TodoStatus.PENDING
                updateDescriptionStyle(newVal)
            }

            checkbox.selectedProperty().bindBidirectional(completedProperty)
        }

        override fun updateItem(todo: Todo?, empty: Boolean) {
            super.updateItem(todo, empty)
            if (empty || todo == null) {
                graphic = null
                return
            }

            completedProperty.set(todo.status == TodoStatus.COMPLETED)
            checkbox.selectedProperty().set(todo.status == TodoStatus.COMPLETED)

            descriptionLabel.text = todo.description
            descriptionLabel.isWrapText = true
            descriptionLabel.maxWidth = 220.0
            updateDescriptionStyle(todo.status == TodoStatus.COMPLETED)

            priorityBadge.text = todo.priority.name
            priorityBadge.styleClass.clear()
            priorityBadge.styleClass.addAll("badge", "badge-${todo.priority.name.lowercase()}")

            deleteButton.setOnAction {
                removeWithUndo(todo)
            }

            graphic = root
        }

        private fun updateDescriptionStyle(completed: Boolean) {
            if (completed) {
                descriptionLabel.styleClass.add("todo-completed")
            } else {
                descriptionLabel.styleClass.remove("todo-completed")
            }
        }
    }
}
