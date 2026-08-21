package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.agent.AgentManager
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Component

/** Persists managed workspace mutations as passive conversation context. */
@Component
class WorkspaceFileActivityNotificationService(
    eventBus: WorkspaceFileActivityEventBus,
    private val agentManager: AgentManager,
) : DisposableBean {
    private val registration = eventBus.addListener(::appendToConversation)

    override fun destroy() {
        registration.close()
    }

    private fun appendToConversation(activity: WorkspaceFileActivity) {
        agentManager.appendSystemMessage(activity.message)
    }
}
