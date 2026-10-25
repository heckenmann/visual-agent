package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDownloadPhase
import de.heckenmann.visualagent.agent.tools.api.ToolWorkspaceFile
import de.heckenmann.visualagent.model.ToolLiveArtifact
import de.heckenmann.visualagent.model.ToolLiveProgress
import de.heckenmann.visualagent.model.ToolLiveState
import java.util.concurrent.atomic.AtomicReference

/** Combines canonical transfer phases and measured byte counts without losing a paused state. */
internal class WorkspaceDownloadLiveReporter(
    private val reporter: ToolLiveReporter,
) {
    private val startedNanos = System.nanoTime()
    private val current = AtomicReference(ToolLiveState("", 0, "Connecting"))

    /** Whether the server's canonical transfer control reported cancellation. */
    val isCancelled: Boolean
        get() = current.get().status == "Cancelled"

    /** Reports real byte counts; an in-flight read cannot overwrite a concurrently paused phase. */
    fun progress(
        bytes: Long,
        total: Long?,
    ) {
        val elapsed = (System.nanoTime() - startedNanos).coerceAtLeast(1).toDouble() / 1_000_000_000
        reporter.report(
            current.updateAndGet {
                it.copy(
                    status =
                        if (it.status ==
                            "Connecting"
                        ) {
                            "Downloading"
                        } else {
                            it.status
                        },
                    progress = ToolLiveProgress(bytes, total, "bytes", bytes / elapsed),
                )
            },
        )
    }

    /** Reflects the existing server lifecycle, including pause/resume from the Files panel. */
    fun phase(phase: ToolDownloadPhase) {
        val label =
            when (phase) {
                ToolDownloadPhase.STARTED -> "Connecting"
                ToolDownloadPhase.PAUSED -> "Paused"
                ToolDownloadPhase.RESUMED -> "Downloading"
                ToolDownloadPhase.COMPLETED -> "Completed"
                ToolDownloadPhase.CANCELLED -> "Cancelled"
                ToolDownloadPhase.FAILED -> "Failed"
            }
        reporter.report(current.updateAndGet { it.copy(status = label) })
    }

    /** Retains the registered file identity only after validation and registration have succeeded. */
    fun complete(file: ToolWorkspaceFile) {
        reporter.report(
            current.updateAndGet {
                it.copy(
                    status = "Completed",
                    progress = ToolLiveProgress(file.sizeBytes, file.sizeBytes, "bytes"),
                    artifacts = listOf(ToolLiveArtifact(file.id, file.originalName, file.mimeType)),
                )
            },
        )
    }
}
