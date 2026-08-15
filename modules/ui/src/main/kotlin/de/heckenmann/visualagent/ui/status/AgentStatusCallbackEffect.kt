@file:Suppress("ktlint:standard:no-wildcard-imports", "FunctionName")

package de.heckenmann.visualagent.ui.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.AgentActivityPhase
import de.heckenmann.visualagent.protocol.TodoPort
import de.heckenmann.visualagent.protocol.TodoState
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
 * Connects transport-owned agent and todo events to the in-flight indicator.
 *
 * The coordinator emits `STATUS:BUSY` and `STATUS:IDLE` notifications while
 * autonomous work progresses. This effect translates those notifications into
 * [InFlightStateHolder] updates so the header indicator and the conversation
 * inline indicators show active sub-agents and todos.
 *
 * @param inFlight Mutable in-flight state holder owned by the Compose app
 * @param activityPort Transport-owned agent lifecycle events
 * @param todoPort Transport-owned todo events
 */
@Composable
internal fun RegisterAgentStatusCallback(
    inFlight: InFlightStateHolder,
    activityPort: ActivityPort,
    todoPort: TodoPort,
) {
    DisposableEffect(inFlight, activityPort) {
        val handle =
            activityPort.addAgentListener { event ->
                when (event.phase) {
                    AgentActivityPhase.STARTED -> inFlight.markAgentStart(event.agentId)
                    AgentActivityPhase.FINISHED -> inFlight.markAgentEnd(event.agentId)
                }
            }
        onDispose { handle.close() }
    }
    DisposableEffect(inFlight, todoPort) {
        val handle =
            todoPort.addListener { change ->
                val todo = change.todo ?: return@addListener
                val currentTodo = inFlight.state.value.currentTodoInProgress
                val currentTodoId = currentTodo?.id
                if (todo.status == TodoState.IN_PROGRESS) {
                    inFlight.setCurrentTodoInProgress(todo)
                } else if (currentTodoId == todo.id) {
                    inFlight.setCurrentTodoInProgress(null)
                }
            }
        onDispose { handle.close() }
    }
}
