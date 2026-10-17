package de.heckenmann.visualagent.ui.todo

import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.protocol.TodoPort
import de.heckenmann.visualagent.protocol.TodoState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Correlates asynchronous attempt reloads with the latest todo lifecycle snapshot. */
internal class TodoAttemptRefresh {
    private val generations = mutableMapOf<String, Long>()

    /** Keeps open overlays current without restoring an older result over a new execution or edit. */
    suspend fun refresh(
        todo: TodoItem,
        port: TodoPort,
        response: TodoResponseState,
        current: () -> TodoItem?,
        closing: () -> Boolean,
    ) {
        val generation = (generations[todo.id] ?: 0) + 1
        generations[todo.id] = generation
        response.invalidateAcceptance()
        if (todo.status == TodoState.IN_PROGRESS || closing()) return
        val execution = response.executionId
        val snapshot = withContext(Dispatchers.IO) { port.responseSnapshots(setOf(todo.id)).firstOrNull() }
        if (closing() ||
            generations[todo.id] != generation ||
            current() != todo ||
            (response.executionId != execution && response.isStreaming)
        ) {
            return
        }
        response.restore(snapshot?.text.orEmpty(), snapshot?.agentId, snapshot?.attempts.orEmpty())
    }
}
