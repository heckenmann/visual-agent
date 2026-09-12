package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.name

/** Writes workspace text after the facade has supplied hardened path and metadata callbacks. */
internal class WorkspaceFileWriteOperations(
    private val workspaceRoot: () -> Path,
    private val databasePath: String,
    private val ensureDirectory: (Path, Path) -> Unit,
    private val recordFile: (Path, String, String?) -> WorkspaceFileRecord,
    private val recordActivity: (String, String?, String?, String?, Long?) -> Unit,
) {
    fun writeText(
        relativePath: String,
        content: String,
    ): WorkspaceFileRecord {
        val target = WorkspaceFilePaths.resolveWorkspacePath(relativePath, databasePath)
        val root = workspaceRoot().toRealPath()
        ensureDirectory(requireNotNull(target.parent) { "Workspace file must have a parent directory" }, root)
        require(!Files.exists(target, LinkOption.NOFOLLOW_LINKS) || Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            "Workspace target is not a regular file"
        }
        Files.writeString(target, content, Charsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        return recordFile(target, target.name, "text/plain").also {
            recordActivity("Workspace file written: ${it.relativePath}.", it.relativePath, "write", it.mimeType, it.sizeBytes)
        }
    }
}
