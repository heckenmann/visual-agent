package de.heckenmann.visualagent.ui.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import de.heckenmann.visualagent.protocol.WorkspaceFilePort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Runs download controls in the lifetime of the Files panel. */
@Composable
internal fun rememberDownloadControls(
    port: WorkspaceFilePort,
    refreshWorkspace: suspend () -> Unit,
    setStatus: (String) -> Unit,
): WorkspaceDownloadControls {
    val scope = rememberCoroutineScope()
    var pendingDownloads by remember(port) { mutableStateOf(emptySet<String>()) }

    /** Runs one workspace download control at a time per download. */
    fun run(
        id: String,
        action: String,
        operation: () -> Unit,
    ) {
        if (id in pendingDownloads) return
        pendingDownloads = pendingDownloads + id
        scope.launch {
            try {
                withContext(Dispatchers.IO) { operation() }
                refreshWorkspace()
                setStatus("Download control completed: $action")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                setStatus("Could not $action download: ${failure.message.orEmpty()}")
            } finally {
                pendingDownloads = pendingDownloads - id
            }
        }
    }

    return WorkspaceDownloadControls({ id -> id !in pendingDownloads }, ::run)
}

/** UI bindings for per-download control availability and execution. */
internal data class WorkspaceDownloadControls(
    val isEnabled: (String) -> Boolean,
    val run: (String, String, () -> Unit) -> Unit,
)
