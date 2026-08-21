package de.heckenmann.visualagent.workspace

import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

/** Lists workspace-relative directories independently from file metadata operations. */
internal fun listWorkspaceDirectories(
    root: Path,
    databasePath: String,
): List<String> =
    Files
        .walk(root)
        .use { paths ->
            paths
                .asSequence()
                .filter { it != root && Files.isDirectory(it) }
                .map { WorkspaceFilePaths.relativePath(it, databasePath) }
                .sorted()
                .toList()
        }
