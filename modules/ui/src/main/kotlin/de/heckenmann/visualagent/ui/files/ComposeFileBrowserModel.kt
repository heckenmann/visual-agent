package de.heckenmann.visualagent.ui.files

import de.heckenmann.visualagent.protocol.WorkspaceFile

/** One direct child directory in the current workspace-browser location. */
internal data class WorkspaceDirectoryEntry(
    val name: String,
    val relativePath: String,
)

/** Files and directories directly visible in one browser location. */
internal data class WorkspaceBrowserListing(
    val directories: List<WorkspaceDirectoryEntry>,
    val files: List<WorkspaceFile>,
)

/** Builds a deterministic browser listing from flat managed workspace records. */
internal fun browseWorkspaceFiles(
    files: List<WorkspaceFile>,
    directory: String,
    workspaceDirectories: List<String> = emptyList(),
): WorkspaceBrowserListing {
    val prefix = directory.trim('/').let { if (it.isBlank()) "" else "$it/" }
    val directories =
        (files.map(WorkspaceFile::relativePath) + workspaceDirectories)
            .mapNotNull { path ->
                val normalized = path.replace('\\', '/')
                val remainder = normalized.removePrefix(prefix)
                if (remainder == normalized && prefix.isNotEmpty()) return@mapNotNull null
                val child = remainder.substringBefore('/')
                val isDirectDirectory =
                    normalized in workspaceDirectories &&
                        remainder.isNotBlank() &&
                        '/' !in remainder
                if (child.isBlank() || ('/' !in remainder && !isDirectDirectory)) {
                    null
                } else {
                    WorkspaceDirectoryEntry(
                        child,
                        "$prefix$child".trim('/'),
                    )
                }
            }.distinctBy(WorkspaceDirectoryEntry::relativePath)
            .sortedBy(WorkspaceDirectoryEntry::name)
    val directFiles =
        files
            .filter { file ->
                val normalized = file.relativePath.replace('\\', '/')
                normalized.startsWith(prefix) && '/' !in normalized.removePrefix(prefix)
            }.sortedBy { it.originalName.lowercase() }
    return WorkspaceBrowserListing(directories, directFiles)
}
