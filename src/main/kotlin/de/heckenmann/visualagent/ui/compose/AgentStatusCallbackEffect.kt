@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import de.heckenmann.visualagent.agent.AgentStatusCallbackAdapter

/**
 * Connects the [AgentStatusCallbackAdapter] to the in-flight indicator.
 *
 * The coordinator emits `STATUS:BUSY` and `STATUS:IDLE` notifications while
 * autonomous work progresses. This effect translates those notifications into
 * [InFlightStateHolder] updates so the header indicator shows active sub-agents.
 *
 * @param inFlight Mutable in-flight state holder owned by the Compose app
 * @param adapter Spring-managed callback adapter
 */
@Composable
internal fun RegisterAgentStatusCallback(
    inFlight: InFlightStateHolder,
    adapter: AgentStatusCallbackAdapter,
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
}
