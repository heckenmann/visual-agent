package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.agent.tools.TerminalSandboxRequest
import de.heckenmann.visualagent.protocol.DirectoryAccessMode
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/** Filesystem access resolved from the server's current workspace or directory grant. */
internal data class ServerTerminalDirectory(
    val root: Path,
    val workingDirectory: Path,
    val mode: DirectoryAccessMode,
) {
    val identity: String = Files.readAttributes(root, "unix:dev,ino", LinkOption.NOFOLLOW_LINKS).let { "${it["dev"]}:${it["ino"]}" }
}

/** Resolves a server terminal invocation against the same grant rules as the file tools. */
@Service
internal class ServerTerminalDirectories(
    private val workspace: WorkspaceFileService,
    private val grants: DirectoryGrantService,
) {
    private val paths = DirectoryGrantPathPolicy()

    /** Resolves the target directory and preserves its grant's access mode. */
    fun resolve(request: TerminalSandboxRequest): ServerTerminalDirectory {
        val grant = request.grantId?.let(grants::requireServerGrant)
        val relativePath = request.workingDirectory.orEmpty()
        val root =
            if (grant == null) {
                workspace.workspaceRoot()
            } else {
                Path.of(requireNotNull(grant.canonicalRoot)).also {
                    require(it.toRealPath() == it) { "The granted root has changed" }
                }
            }
        val directory =
            if (grant == null) {
                if (relativePath.isBlank()) root else WorkspaceFilePaths.resolveWorkspacePath(relativePath, root).toRealPath()
            } else {
                paths.resolveRelative(grant, relativePath).toRealPath()
            }
        require(
            directory.startsWith(root) && Files.isDirectory(directory),
        ) { "Working directory is outside the authorized root or unavailable" }
        return ServerTerminalDirectory(root, directory, grant?.mode ?: DirectoryAccessMode.READ_WRITE)
    }
}
