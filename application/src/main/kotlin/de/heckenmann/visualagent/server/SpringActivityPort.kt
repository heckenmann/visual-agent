package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.AgentStatusCallbackAdapter
import de.heckenmann.visualagent.agent.tools.ToolCallPhase
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.AgentActivity
import de.heckenmann.visualagent.protocol.AgentActivityPhase
import de.heckenmann.visualagent.protocol.ToolActivity
import de.heckenmann.visualagent.protocol.ToolActivityPhase
import org.springframework.stereotype.Component

/** Translates application event buses into protocol-owned activity events. */
@Component
class SpringActivityPort(
    private val toolEventBus: ToolEventBus,
    private val agentStatusCallbackAdapter: AgentStatusCallbackAdapter,
) : ActivityPort {
    override fun addToolListener(listener: (ToolActivity) -> Unit): AutoCloseable =
        toolEventBus.addListener { event ->
            listener(
                ToolActivity(
                    toolId = event.toolId,
                    requestId = event.context["requestId"]?.toString(),
                    phase =
                        when (event.phase) {
                            ToolCallPhase.STARTED -> ToolActivityPhase.STARTED
                            ToolCallPhase.FINISHED -> ToolActivityPhase.FINISHED
                        },
                    success = event.result.success,
                ),
            )
        }

    override fun addAgentListener(listener: (AgentActivity) -> Unit): AutoCloseable =
        agentStatusCallbackAdapter.addListener { agentId, message ->
            when {
                message.startsWith("STATUS:BUSY") -> listener(AgentActivity(agentId, AgentActivityPhase.STARTED))
                message.startsWith("STATUS:IDLE") -> listener(AgentActivity(agentId, AgentActivityPhase.FINISHED))
            }
        }
}
