package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.todo.Todo
import de.heckenmann.visualagent.todo.TodoManager
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.ui.ConfirmationDialogs
import de.heckenmann.visualagent.ui.FxmlLoader
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.TextInputControl
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

/**
 * Todo management panel with filtering, bulk actions, keyboard shortcuts, and shared manager binding.
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
    private lateinit var emptyAddButton: Button

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
    private lateinit var todoEmptyState: VBox

    @FXML
    private lateinit var todoEmptyTitleLabel: Label

    @FXML
    private lateinit var todoEmptyDescriptionLabel: Label

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
                    removeTodo(selected)
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
     * Wires FXML controls, list rendering, keyboard shortcuts, and manager-backed state refresh.
     */
    @FXML
    fun initialize() {
        todoListView.items = todos
        todoListView.placeholder = Region()
        todoListView.setCellFactory {
            TodoCell(
                onStatusChanged = { todo, completed ->
                    todoManager.updateStatus(
                        todo.id,
                        if (completed) TodoStatus.COMPLETED else TodoStatus.PENDING,
                    )
                },
                onRemove = ::removeTodo,
            )
        }
        todos.addListener(ListChangeListener { updateSummary() })
        addButton.setOnAction { showAddDialog() }
        emptyAddButton.setOnAction { showAddDialog() }
        editButton.setOnAction { editSelectedTodo() }
        completeAllButton.setOnAction { completeAllTodos() }
        deleteCompletedButton.setOnAction { deleteCompletedTodos() }
        statusFilterSelector.items.setAll("All", "Open", "Active", "Done", "Cancelled")
        statusFilterSelector.selectionModel.select("All")
        statusFilterSelector.selectionModel.selectedItemProperty().addListener { _, _, selected ->
            statusFilter = selected ?: "All"
            refreshFromManager()
        }
        todoListView.selectionModel.selectedItemProperty().addListener { _, _, _ -> updateActionStates() }
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

    private fun showAddDialog() {
        showAddTodoDialog()?.let { result ->
            todoManager.add(result.description, result.priority)
        }
    }

    private fun editSelectedTodo() {
        val selected = todoListView.selectionModel.selectedItem ?: return
        showEditTodoDialog(selected)?.let { result ->
            todoManager.update(selected.id, result.description, result.priority)
            refreshFromManager()
        }
    }

    private fun completeAllTodos() {
        todoManager.getAll().forEach { todo ->
            if (todo.status != TodoStatus.COMPLETED) {
                todoManager.updateStatus(todo.id, TodoStatus.COMPLETED)
            }
        }
    }

    private fun deleteCompletedTodos() {
        val completed = todoManager.getAll().filter { it.status == TodoStatus.COMPLETED }
        completed.forEach { todoManager.remove(it.id) }
        refreshFromManager()
    }

    /**
     * Removes a todo from the shared manager.
     */
    private fun removeTodo(todo: Todo) {
        if (!ConfirmationDialogs.confirm("Delete Todo", "This action cannot be undone", "Delete \"${todo.description}\"?")) return
        todos.remove(todo)
        todoManager.remove(todo.id)
    }

    /**
     * Reloads todos from the shared manager and applies the current status filter.
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
            syncTodoEmptyState(source.isEmpty(), filtered.isEmpty())
            updateSummary()
        }
    }

    private fun updateSummary() {
        val allTodos = todoManager.getAll()
        val total = allTodos.size
        val open = allTodos.count { it.status == TodoStatus.PENDING }
        val inProgress = allTodos.count { it.status == TodoStatus.IN_PROGRESS }
        val done = allTodos.count { it.status == TodoStatus.COMPLETED }
        totalCountLabel.text = total.toString()
        openCountLabel.text = open.toString()
        inProgressCountLabel.text = inProgress.toString()
        doneCountLabel.text = done.toString()
        syncTodoEmptyState(allTodos.isEmpty(), todos.isEmpty())
        updateActionStates(allTodos)
    }

    private fun syncTodoEmptyState(
        sourceIsEmpty: Boolean,
        filteredIsEmpty: Boolean,
    ) {
        updateTodoEmptyState(
            todoEmptyState,
            emptyAddButton,
            todoEmptyTitleLabel,
            todoEmptyDescriptionLabel,
            sourceIsEmpty,
            filteredIsEmpty,
        )
    }

    private fun updateActionStates(allTodos: List<Todo> = todoManager.getAll()) {
        updateTodoActionStates(
            editButton,
            completeAllButton,
            deleteCompletedButton,
            todoListView.selectionModel.selectedItem,
            allTodos,
        )
    }

    override fun layoutChildren() {
        rootBorderPane.resizeRelocate(0.0, 0.0, width, height)
    }
}
