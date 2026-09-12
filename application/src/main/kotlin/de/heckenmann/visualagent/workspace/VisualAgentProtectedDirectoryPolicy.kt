package de.heckenmann.visualagent.workspace

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path

/**
 * Rejects directory grants that would expose Visual Agent's own configuration or data.
 *
 * A root is unsafe both when it is one of the protected directories and when it is an ancestor
 * of one. The latter prevents indirectly exposing the SQLite database and its credentials by
 * granting a parent such as the application working directory.
 */
@Service
class VisualAgentProtectedDirectoryPolicy(
    @Qualifier("databasePath") databasePath: String,
) {
    private val protectedRoots = protectedRoots(databasePath)

    /** Fails when [directory] is or contains a Visual Agent-owned protected directory. */
    fun requireGrantable(directory: Path) {
        val root = directory.toRealPath()
        require(protectedRoots.none { protected -> root == protected || protected.startsWith(root) }) {
            "ACCESS_DENIED: Visual Agent configuration and data directories cannot be granted"
        }
    }

    private fun protectedRoots(databasePath: String): Set<Path> =
        buildSet {
            databaseParent(databasePath)?.let(::add)
            Path.of("application", "src", "main", "resources", "config").existingCanonicalDirectory()?.let(::add)
        }

    private fun databaseParent(databasePath: String): Path? {
        val raw = databasePath.removePrefix("jdbc:sqlite:")
        if (raw.isBlank() || raw == ":memory:" || raw.startsWith("file:")) return null
        return Path
            .of(raw)
            .toAbsolutePath()
            .normalize()
            .parent
            ?.existingCanonicalDirectory()
    }

    private fun Path.existingCanonicalDirectory(): Path? = takeIf { Files.isDirectory(it) }?.toRealPath()
}
