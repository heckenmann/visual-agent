package de.heckenmann.visualagent.workspace

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Copies server-owned grant files and verifies the target before a move removes its source. */
internal class DirectoryGrantTransferOperations(
    private val authorizeSource: (String, String, Boolean) -> Path,
    private val authorizeTarget: (String, String) -> Path,
    private val targetRelativePath: (String, Path) -> String,
) {
    fun copy(
        sourceGrantId: String,
        sourcePath: String,
        targetGrantId: String,
        targetPath: String,
    ): String {
        val target = authorizeTarget(targetGrantId, targetPath)
        copyVerified(authorizeSource(sourceGrantId, sourcePath, false), target)
        return targetRelativePath(targetGrantId, target.toRealPath())
    }

    fun move(
        sourceGrantId: String,
        sourcePath: String,
        targetGrantId: String,
        targetPath: String,
    ): String {
        val source = authorizeSource(sourceGrantId, sourcePath, true)
        val target = authorizeTarget(targetGrantId, targetPath)
        copyVerified(source, target)
        Files.delete(source)
        return targetRelativePath(targetGrantId, target.toRealPath())
    }

    private fun copyVerified(
        source: Path,
        target: Path,
    ) {
        val sourceSize = Files.size(source)
        val sourceHash = WorkspaceFilePaths.sha256(source)
        val temporary = Files.createTempFile(requireNotNull(target.parent), ".visual-agent-transfer-", ".part")
        try {
            Files.copy(source, temporary, REPLACE_EXISTING)
            require(Files.size(temporary) == sourceSize && WorkspaceFilePaths.sha256(temporary) == sourceHash) {
                "FILE_CHANGED_DURING_TRANSFER: copied file verification failed"
            }
            try {
                Files.move(temporary, target, ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, target)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
