package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import java.nio.file.Path
import kotlin.io.path.isRegularFile

/** Registers a validated, completed remote download in the managed workspace. */
fun WorkspaceFileService.registerDownloadedFile(
    destination: Path,
    originalName: String,
    detectedMimeType: String,
    sizeBytes: Long,
    sha256: String,
): WorkspaceFileRecord {
    val root = workspaceRoot().toRealPath()
    val parent = destination.parent?.toRealPath()
    require(parent != null && parent.startsWith(root)) { "Downloaded file must remain inside the workspace" }
    require(destination.isRegularFile()) { "Downloaded file does not exist" }
    require(detectedMimeType.isNotBlank()) { "Downloaded file MIME type is empty" }
    return recordManagedFile(destination, originalName, detectedMimeType, sizeBytes, sha256)
}
