package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.agent.AgentManager
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
        agentManager.appendSystemMessage(
            content = activity.message,
            metadata =
                buildJsonObject {
                    put("type", "workspace_file")
                    put("eventType", "workspace_file_${activity.operation ?: "mutation"}")
                    activity.relativePath?.let { put("workspacePath", it) }
                    activity.operation?.let { put("operation", it) }
                    put("status", if (activity.success) "success" else "failure")
                    activity.mimeType?.let { put("mimeType", it) }
                    activity.sizeBytes?.let { put("sizeBytes", it) }
                }.toString(),
            contextPolicy = activity.contextPolicy,
        )
    }
}
