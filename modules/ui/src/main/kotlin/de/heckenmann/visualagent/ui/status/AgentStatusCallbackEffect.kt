@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import de.heckenmann.visualagent.agent.AgentStatusCallbackAdapter
import de.heckenmann.visualagent.todo.TodoEventBus
import de.heckenmann.visualagent.todo.TodoStatus
import de.heckenmann.visualagent.ui.agents.*
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.canvas.*
import de.heckenmann.visualagent.ui.components.*
import de.heckenmann.visualagent.ui.conversation.*
import de.heckenmann.visualagent.ui.files.*
import de.heckenmann.visualagent.ui.modal.*
import de.heckenmann.visualagent.ui.settings.*
import de.heckenmann.visualagent.ui.status.*
import de.heckenmann.visualagent.ui.todo.*
import de.heckenmann.visualagent.ui.workspace.*

/**
 * Connects the [AgentStatusCallbackAdapter] and [TodoEventBus] to the in-flight indicator.
 *
 * The coordinator emits `STATUS:BUSY` and `STATUS:IDLE` notifications while
 * autonomous work progresses. This effect translates those notifications into
 * [InFlightStateHolder] updates so the header indicator and the conversation
 * inline indicators show active sub-agents and todos.
 *
 * @param inFlight Mutable in-flight state holder owned by the Compose app
 * @param adapter Spring-managed callback adapter
 * @param todoEventBus Spring-managed todo event bus
 */
@Composable
internal fun RegisterAgentStatusCallback(
    inFlight: InFlightStateHolder,
    adapter: AgentStatusCallbackAdapter,
    todoEventBus: TodoEventBus,
) {
    DisposableEffect(inFlight, adapter) {
        adapter.register { agentId, message ->
            when {
                message.startsWith("STATUS:BUSY") -> inFlight.markAgentStart(agentId)
                message.startsWith("STATUS:IDLE") -> inFlight.markAgentEnd(agentId)
            }
        }
        onDispose { adapter.register { _, _ -> } }
    }
    DisposableEffect(inFlight, todoEventBus) {
        val handle =
            todoEventBus.addListener { change ->
                val todo = change.todo ?: return@addListener
                val currentTodo = inFlight.state.value.currentTodoInProgress
                val currentTodoId = currentTodo?.id
                if (todo.status == TodoStatus.IN_PROGRESS) {
                    inFlight.setCurrentTodoInProgress(todo)
                } else if (currentTodoId == todo.id) {
                    inFlight.setCurrentTodoInProgress(null)
                }
            }
        onDispose { handle.close() }
    }
}
