package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.protocol.DownloadActivity
import de.heckenmann.visualagent.protocol.DownloadActivityStatus
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Component

/** Adds download lifecycle messages to the conversation and main-agent context. */
@Component
class WorkspaceDownloadNotificationService(
    private val eventBus: WorkspaceDownloadEventBus,
    private val agentManager: AgentManager,
) : DisposableBean {
    private val registration = eventBus.addListener(::notifyConversation)

    override fun destroy() {
        registration.close()
    }

    private fun notifyConversation(event: DownloadActivity) {
        agentManager.appendSystemMessage(event.toConversationMessage())
    }
}

private fun DownloadActivity.toConversationMessage(): String {
    val progress =
        totalBytes?.let { total -> " ($downloadedBytes / $total bytes)" }
            ?: " ($downloadedBytes bytes)"
    return when (status) {
        DownloadActivityStatus.STARTED -> "Workspace download started: $relativePath$progress."
        DownloadActivityStatus.PAUSED -> "Workspace download paused: $relativePath$progress."
        DownloadActivityStatus.RESUMED -> "Workspace download resumed: $relativePath$progress."
        DownloadActivityStatus.COMPLETED ->
            "Workspace download completed: $relativePath$progress. The managed file is now available to the main agent."
        DownloadActivityStatus.CANCELLED -> "Workspace download cancelled: $relativePath$progress."
        DownloadActivityStatus.FAILED ->
            "Workspace download failed: $relativePath${detail?.let { ": $it" }.orEmpty()}$progress."
    }
}
