package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.config.AppConfig
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

/**
 * Filesystem helpers for the managed workspace file area.
 *
 * Use cases: UC-0000023, UC-0000024, UC-0000026, UC-0000027, UC-0000031.
 */
internal object WorkspaceFilePaths {
    const val CANVAS_MIME_TYPE = "application/vnd.visual-agent.canvas+xml"
    val TEXT_EXTENSIONS = setOf("txt", "md", "csv", "json", "xml", "log", "kt", "java", "draw", "jhd", "canvas")

    /** Returns the managed workspace root directory, creating it when necessary. */
    fun workspaceRoot(): Path {
        val dbPath = normalizedDatabasePath()
        val parent = dbPath.parent ?: Path.of("data").toAbsolutePath().normalize()
        return parent
            .resolve("workspace")
            .toAbsolutePath()
            .normalize()
            .also { it.createDirectories() }
    }

    /** Resolves a workspace-relative file path and rejects path traversal. */
    fun resolveManagedPath(relativePath: String): Path {
        val root = workspaceRoot()
        val resolved = root.resolve(normalizeRelativePath(relativePath)).normalize()
        require(resolved.startsWith(root)) { "Path escapes workspace root" }
        require(resolved.exists() && resolved.isRegularFile()) { "Workspace file does not exist" }
        return resolved
    }

    /** Returns a path relative to the managed workspace root. */
    fun relativePath(path: Path): String = workspaceRoot().relativize(path.toAbsolutePath().normalize()).toString()

    /** Normalizes persisted workspace-relative paths to a platform-neutral form. */
    fun normalizeRelativePath(path: String): String = path.replace('\\', '/').trim().removePrefix("/")

    /** Sanitizes a workspace subdirectory name. */
    fun safeDirectoryName(name: String): String =
        safeFileName(name)
            .replace(" ", "-")
            .ifBlank { "generated" }

    /** Finds a non-existing destination path for a requested filename. */
    fun uniqueDestination(
        directory: Path,
        requestedName: String,
    ): Path {
        val safeName = safeFileName(requestedName)
        val base = safeName.substringBeforeLast('.', safeName)
        val extension = safeName.substringAfterLast('.', "").let { if (it.isBlank() || it == safeName) "" else ".$it" }
        var candidate = directory.resolve("$base$extension")
        var index = 1
        while (candidate.exists()) {
            candidate = directory.resolve("$base-$index$extension")
            index++
        }
        return candidate
    }

    /** Sanitizes a user-facing filename for managed storage. */
    fun safeFileName(name: String): String =
        Path
            .of(name)
            .fileName
            .toString()
            .ifBlank { "file" }
            .replace(Regex("""[^\w.\- ]"""), "_")

    /** Preserves the current extension when a rename request omits it. */
    fun preserveExtensionIfMissing(
        source: Path,
        requestedName: String,
    ): String =
        if (Path.of(requestedName).extension.isBlank() && source.extension.isNotBlank()) {
            "$requestedName.${source.extension}"
        } else {
            requestedName
        }

    /** Detects MIME type with stable fallbacks for workspace-supported formats. */
    fun detectMimeType(path: Path): String =
        Files.probeContentType(path)
            ?: when (path.extension.lowercase()) {
                "txt", "md", "csv", "json", "xml", "kt", "java", "log" -> "text/plain"
                "draw", "jhd", "canvas" -> CANVAS_MIME_TYPE
                "pdf" -> "application/pdf"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "gif" -> "image/gif"
                else -> "application/octet-stream"
            }

    /** Computes the SHA-256 hash of file bytes. */
    fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun normalizedDatabasePath(): Path {
        val raw = AppConfig.instance.databasePath.removePrefix("jdbc:sqlite:")
        val path = Path.of(raw.ifBlank { "./data/visual-agent.db" })
        return if (path.isAbsolute) path.normalize() else Path.of(System.getProperty("user.dir")).resolve(path).normalize()
    }
}
