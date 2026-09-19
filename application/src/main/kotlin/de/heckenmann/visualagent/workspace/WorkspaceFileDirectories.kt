package de.heckenmann.visualagent.workspace

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/** Creates workspace directories while rejecting links and paths outside the root. */
internal object WorkspaceFileDirectories {
    fun ensure(
        directory: Path,
        root: Path,
    ) {
        require(directory.startsWith(root)) { "Workspace directory escapes the workspace" }
        var current = root
        root.relativize(directory).forEach { segment ->
            current = current.resolve(segment)
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                require(Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) { "Workspace path contains a symbolic link or file" }
            } else {
                Files.createDirectory(current)
            }
            require(current.toRealPath().startsWith(root)) { "Workspace directory escapes the workspace" }
        }
    }
}
