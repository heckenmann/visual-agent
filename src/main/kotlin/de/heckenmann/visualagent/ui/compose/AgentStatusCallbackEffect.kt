@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import de.heckenmann.visualagent.agent.AgentManager

/**
 * Connects the global sub-agent status callback to the in-flight indicator.
 *
 * The coordinator emits `STATUS:BUSY` and `STATUS:IDLE` notifications while
 * autonomous work progresses. This effect translates those notifications into
 * [InFlightStateHolder] updates so the header indicator shows active sub-agents.
 *
 * @param inFlight Mutable in-flight state holder owned by the Compose app
 */
@Composable
internal fun RegisterAgentStatusCallback(inFlight: InFlightStateHolder) {
    DisposableEffect(inFlight) {
        AgentManager.setAgentCallback { agentId, message ->
            when {
                message.startsWith("STATUS:BUSY") -> inFlight.markAgentStart(agentId)
                message.startsWith("STATUS:IDLE") -> inFlight.markAgentEnd(agentId)
            }
        }
        onDispose { AgentManager.setAgentCallback { _, _ -> } }
    }
}
