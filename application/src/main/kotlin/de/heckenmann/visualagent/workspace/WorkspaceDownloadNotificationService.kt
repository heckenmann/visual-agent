package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.ConversationContextPolicy
import de.heckenmann.visualagent.protocol.DownloadActivity
import de.heckenmann.visualagent.protocol.DownloadActivityStatus
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
        agentManager.appendSystemMessage(
            content = event.toConversationMessage(),
            metadata = event.toMetadata(),
            contextPolicy = event.contextPolicy(),
        )
    }
}

private fun DownloadActivity.contextPolicy(): ConversationContextPolicy =
    when (status) {
        DownloadActivityStatus.COMPLETED, DownloadActivityStatus.FAILED -> ConversationContextPolicy.SUMMARY_SOURCE
        DownloadActivityStatus.STARTED,
        DownloadActivityStatus.PAUSED,
        DownloadActivityStatus.RESUMED,
        DownloadActivityStatus.CANCELLED,
        -> ConversationContextPolicy.AUDIT_ONLY
    }

private fun DownloadActivity.toMetadata(): String =
    buildJsonObject {
        put("type", "workspace_download")
        put("eventType", "workspace_download_${status.name.lowercase()}")
        put("downloadId", id)
        put("workspacePath", relativePath)
        put("status", status.name.lowercase())
        put("downloadedBytes", downloadedBytes)
        totalBytes?.let { put("totalBytes", it) }
        mimeType?.let { put("mimeType", it) }
        sizeBytes?.let { put("sizeBytes", it) }
        sha256?.let { put("sha256", it) }
        validationResult?.let { put("validationResult", it) }
        detail?.let { put("detail", it.take(500)) }
    }.toString()

private fun DownloadActivity.toConversationMessage(): String {
    val progress =
        totalBytes?.let { total -> " ($downloadedBytes / $total bytes)" }
            ?: " ($downloadedBytes bytes)"
    return when (status) {
        DownloadActivityStatus.STARTED -> "Workspace download started: $relativePath$progress."
        DownloadActivityStatus.PAUSED -> "Workspace download paused: $relativePath$progress."
        DownloadActivityStatus.RESUMED -> "Workspace download resumed: $relativePath$progress."
        DownloadActivityStatus.COMPLETED ->
            "Workspace download completed: $relativePath$progress. " +
                "The managed file is now available to the main agent" +
                completionDetails() +
                "."
        DownloadActivityStatus.CANCELLED -> "Workspace download cancelled: $relativePath$progress."
        DownloadActivityStatus.FAILED ->
            "Workspace download failed: $relativePath${detail?.let { ": $it" }.orEmpty()}$progress."
    }
}

private fun DownloadActivity.completionDetails(): String {
    val details =
        listOfNotNull(
            mimeType?.let { "MIME $it" },
            sizeBytes?.let { "$it bytes" },
            validationResult?.let { "validation $it" },
        ).joinToString(", ")
    return details.takeIf(String::isNotBlank)?.let { " ($it)" }.orEmpty()
}
