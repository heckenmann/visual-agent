package de.heckenmann.visualagent.todo

import de.heckenmann.visualagent.knowledge.TodoCreation
import de.heckenmann.visualagent.knowledge.TodoStore

/** In-memory todo store used by manager unit tests. */
internal class InMemoryTodoStore : TodoStore {
    private val todos = mutableListOf<Todo>()

    override fun saveTodo(todo: Todo) {
        todos.removeIf { it.id == todo.id }
        todos.add(todo)
    }

    override fun createTodoIfAbsent(todo: Todo): TodoCreation {
        val normalized =
            todo.description
                .trim()
                .replace(Regex("\\s+"), " ")
                .lowercase()
        val existing =
            todos.firstOrNull {
                it.description
                    .trim()
                    .replace(Regex("\\s+"), " ")
                    .lowercase() == normalized
            }
        return if (existing == null) {
            saveTodo(todo)
            TodoCreation(todo, created = true)
        } else {
            TodoCreation(existing, created = false)
        }
    }

    override fun listTodos(): List<Todo> = todos.toList()

    override fun deleteTodo(todoId: String) {
        todos.removeIf { it.id == todoId }
    }

    override fun clearTodos() {
        todos.clear()
    }
}
