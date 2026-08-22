@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.WorkspaceDownload
import de.heckenmann.visualagent.protocol.WorkspaceDownloadState
import de.heckenmann.visualagent.ui.components.ActionIconButton
import de.heckenmann.visualagent.ui.components.PanelContentCard

/** Renders clickable workspace breadcrumbs. */
@Composable
internal fun WorkspaceBreadcrumbs(
    directory: String,
    onNavigate: (String) -> Unit,
) {
    val segments = directory.trim('/').split('/').filter(String::isNotBlank)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onNavigate("") }) { Text("Workspace") }
        var path = ""
        segments.forEach { segment ->
            path = if (path.isBlank()) segment else "$path/$segment"
            Text("/")
            val target = path
            TextButton(onClick = { onNavigate(target) }) { Text(segment) }
        }
    }
}

/** Renders one direct child directory. */
@Composable
internal fun WorkspaceDirectoryRow(
    directory: WorkspaceDirectoryEntry,
    activeDownloads: Int,
    onOpen: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        ActionIconButton(icon = Icons.Filled.Folder, description = "Open folder ${directory.name}", onClick = onOpen)
        TextButton(onClick = onOpen) {
            Text(directory.name + if (activeDownloads > 0) " · $activeDownloads active" else "")
        }
    }
}

/** Renders a running or paused download with controls and bounded progress. */
@Composable
internal fun WorkspaceDownloadRow(
    download: WorkspaceDownload,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    PanelContentCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Downloading ${download.relativePath.substringAfterLast('/')}")
            val total = download.totalBytes
            if (total != null && total > 0) {
                LinearProgressIndicator(
                    progress = { (download.downloadedBytes.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${download.downloadedBytes} / $total bytes")
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("${download.downloadedBytes} bytes")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (download.state == WorkspaceDownloadState.PAUSED) {
                    ActionIconButton(icon = Icons.Filled.PlayArrow, description = "Resume download", onClick = onResume)
                } else {
                    ActionIconButton(icon = Icons.Filled.Pause, description = "Pause download", onClick = onPause)
                }
                ActionIconButton(icon = Icons.Filled.Close, description = "Cancel download", onClick = onCancel)
            }
        }
    }
}
