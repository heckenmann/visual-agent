package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.AgentPort
import de.heckenmann.visualagent.protocol.ApplicationPort
import de.heckenmann.visualagent.protocol.CanvasPort
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.LifecyclePort
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.TodoPort
import de.heckenmann.visualagent.protocol.WorkspaceFilePort
import org.springframework.stereotype.Component

/** Spring composition root for the protocol boundary. */
@Component
class SpringApplicationPort(
    override val conversation: ConversationPort,
    override val todos: TodoPort,
    override val agents: AgentPort,
    override val providers: ProviderPort,
    override val settings: SettingsPort,
    override val workspaceFiles: WorkspaceFilePort,
    override val canvas: CanvasPort,
    override val layout: de.heckenmann.visualagent.protocol.WorkspaceLayoutPort,
    override val activity: ActivityPort,
    override val lifecycle: LifecyclePort,
    private val agentManager: de.heckenmann.visualagent.agent.AgentManager,
) : ApplicationPort {
    override fun cancelActiveWork() {
        agentManager.cancelActiveWork()
    }
}
