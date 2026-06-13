package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoPriority
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.ui.FxmlLoader
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.fxml.FXML
import javafx.geometry.Insets
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.ComboBox
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import javafx.scene.control.TextInputControl
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Represents TodoPanel.
 */
@Component
@Lazy
class TodoPanel(
    private val todoManager: TodoManager,
) : Region() {
    @FXML
    private lateinit var rootBorderPane: BorderPane

    @FXML
    private lateinit var addButton: Button

    @FXML
    private lateinit var editButton: Button

    @FXML
    private lateinit var completeAllButton: Button

    @FXML
    private lateinit var deleteCompletedButton: Button

    @FXML
    private lateinit var totalCountLabel: Label

    @FXML
    private lateinit var openCountLabel: Label

    @FXML
    private lateinit var inProgressCountLabel: Label

    @FXML
    private lateinit var doneCountLabel: Label

    @FXML
    private lateinit var todoListView: ListView<Todo>

    @FXML
    private lateinit var statusFilterSelector: ComboBox<String>

    private val todos = FXCollections.observableArrayList<Todo>()
    private var statusFilter = "All"
    private var registeredScene: javafx.scene.Scene? = null
    private val todoShortcutHandler =
        javafx.event.EventHandler<KeyEvent> { ev ->
            val focusOwner = registeredScene?.focusOwner
            val focusOwnerIsTextInput = focusOwner is TextInputControl
            if (shouldOpenAddTodoDialog(ev.code, ev.isShortcutDown, focusOwnerIsTextInput)) {
                showAddDialog()
                ev.consume()
            } else if (ev.code == KeyCode.DELETE && !focusOwnerIsTextInput) {
                val selected = todoListView.selectionModel.selectedItem
                if (selected != null) {
                    removeWithUndo(selected)
                    ev.consume()
                }
            }
        }

    init {
        val root = FxmlLoader.load(this, "todo-panel.fxml")
        children.add(root)
        // initialize from the shared TodoManager if available
        todos.setAll(todoManager.getAll())
        todoManager.addListener { refreshFromManager() }
    }

    /**
     * Executes initialize.
     */
    @FXML
    fun initialize() {
        todoListView.items = todos
        todoListView.placeholder = Label("No todos yet. Add one or let the agent create tasks.").apply { styleClass.add("todo-empty") }
        todoListView.setCellFactory {
            TodoCell(
                onStatusChanged = { todo, completed ->
                    todoManager.updateStatus(
                        todo.id,
                        if (completed) TodoStatus.COMPLETED else TodoStatus.PENDING,
                    )
                },
                onRemove = ::removeWithUndo,
            )
        }
        todos.addListener(ListChangeListener { updateSummary() })
        addButton.setOnAction { showAddDialog() }
        editButton.setOnAction { editSelectedTodo() }
        completeAllButton.setOnAction { completeAllTodos() }
        deleteCompletedButton.setOnAction { deleteCompletedTodos() }
        statusFilterSelector.items.setAll("All", "Open", "Active", "Done", "Cancelled")
        statusFilterSelector.selectionModel.select("All")
        statusFilterSelector.selectionModel.selectedItemProperty().addListener { _, _, selected ->
            statusFilter = selected ?: "All"
            refreshFromManager()
        }
        updateSummary()
        // Keyboard shortcuts: Cmd/Ctrl+N for new todo, Delete for removing selected todo.
        rootBorderPane.sceneProperty().addListener { _, _, newScene ->
            registeredScene?.removeEventFilter(KeyEvent.KEY_PRESSED, todoShortcutHandler)
            registeredScene = newScene
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, todoShortcutHandler)
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
            todoManager.add(descField.text.trim(), priorityCombo.selectionModel.selectedItem ?: TodoPriority.MEDIUM)
        }
    }

    private fun editSelectedTodo() {
        val selected = todoListView.selectionModel.selectedItem ?: return
        val dialog = Dialog<Boolean>()
        dialog.title = "Edit Todo"
        dialog.dialogPane.styleClass.add("todo-dialog")

        val descField = TextField(selected.description)
        descField.promptText = "Describe the task..."
        descField.prefColumnCount = 30

        val priorityCombo = ComboBox<TodoPriority>(FXCollections.observableArrayList(TodoPriority.entries.toList()))
        priorityCombo.selectionModel.select(selected.priority)

        val content = VBox(14.0)
        content.padding = Insets(24.0)
        content.children.addAll(Label("Task Description"), descField, Label("Priority Level"), priorityCombo)
        dialog.dialogPane.content = content
        dialog.dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)

        dialog.setResultConverter { buttonType ->
            buttonType == ButtonType.OK && descField.text.isNotBlank()
        }

        dialog.showAndWait().ifPresent { changed ->
            if (changed) {
                todoManager.update(
                    selected.id,
                    descField.text.trim(),
                    priorityCombo.selectionModel.selectedItem ?: TodoPriority.MEDIUM,
                )
                refreshFromManager()
            }
        }
    }

    private fun completeAllTodos() {
        todos.forEach { todo ->
            if (todo.status != TodoStatus.COMPLETED) {
                todoManager.updateStatus(todo.id, TodoStatus.COMPLETED)
            }
        }
    }

    private fun deleteCompletedTodos() {
        val completed = todos.filter { it.status == TodoStatus.COMPLETED }.toList()
        completed.forEach { todoManager.remove(it.id) }
        refreshFromManager()
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
        val thread =
            Thread {
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

    /**
     * Executes refreshFromManager.
     */
    fun refreshFromManager() {
        Platform.runLater {
            val source = todoManager.getAll()
            val filtered =
                source.filter { todo ->
                    when (statusFilter) {
                        "Open" -> todo.status == TodoStatus.PENDING
                        "Active" -> todo.status == TodoStatus.IN_PROGRESS
                        "Done" -> todo.status == TodoStatus.COMPLETED
                        "Cancelled" -> todo.status == TodoStatus.CANCELLED
                        else -> true
                    }
                }
            todos.setAll(filtered)
            updateSummary()
        }
    }

    private fun updateSummary() {
        val total = todos.size
        val open = todos.count { it.status == TodoStatus.PENDING }
        val inProgress = todos.count { it.status == TodoStatus.IN_PROGRESS }
        val done = todos.count { it.status == TodoStatus.COMPLETED }
        totalCountLabel.text = total.toString()
        openCountLabel.text = open.toString()
        inProgressCountLabel.text = inProgress.toString()
        doneCountLabel.text = done.toString()
    }
}
