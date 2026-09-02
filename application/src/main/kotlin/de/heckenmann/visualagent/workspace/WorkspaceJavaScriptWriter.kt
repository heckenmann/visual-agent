package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.agent.javascript.JavaScriptWorkspaceDeleteResult
import de.heckenmann.visualagent.agent.javascript.JavaScriptWorkspaceReadLimitExceededException
import de.heckenmann.visualagent.agent.javascript.JavaScriptWorkspaceReadResult
import de.heckenmann.visualagent.agent.javascript.JavaScriptWorkspaceWriteResult
import de.heckenmann.visualagent.agent.javascript.JavaScriptWorkspaceWriter
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isSymbolicLink
import kotlin.math.min

/** Persists JavaScript-generated text through a strict managed-workspace boundary. */
@Service
class WorkspaceJavaScriptWriter(
    private val workspaceFiles: WorkspaceFileService,
    private val activityEvents: WorkspaceFileActivityEventBus,
) : JavaScriptWorkspaceWriter {
    /**
     * Write a UTF-8 text file below the managed workspace and persist its metadata.
     *
     * The path must be relative, may not contain traversal components, and may not
     * resolve through a symbolic link outside the managed workspace.
     */
    override fun write(
        relativePath: String,
        content: String,
    ): JavaScriptWorkspaceWriteResult {
        val normalized = relativePath.replace('\\', '/').trim()
        require(normalized.isNotBlank()) { "Workspace path must not be blank" }
        val path =
            java.nio.file.Path
                .of(normalized)
        require(!path.isAbsolute) { "Workspace path must be relative" }
        require(path.nameCount > 0 && path.any { it.toString() in setOf(".", "..") }.not()) {
            "Workspace path traversal is not allowed"
        }
        val root = workspaceFiles.workspaceRoot().toAbsolutePath().normalize()
        val realRoot = root.toRealPath()
        val destination = root.resolve(normalized).normalize()
        require(destination.startsWith(root)) { "Workspace path escapes the workspace" }
        destination.parent?.let { createSafeDirectories(root, it, realRoot) }
        val realParent = (destination.parent ?: root).toRealPath()
        require(realParent.startsWith(realRoot)) { "Workspace path escapes the workspace" }
        require(!destination.isSymbolicLink()) { "Workspace symbolic links are not writable" }
        Files.writeString(
            destination,
            content,
            StandardCharsets.UTF_8,
        )
        val record = workspaceFiles.recordManagedFile(destination, destination.fileName.toString())
        activityEvents.publish(
            WorkspaceFileActivity(
                "Workspace file written by JavaScript: ${record.relativePath}.",
                record.relativePath,
                "write",
                mimeType = record.mimeType,
                sizeBytes = record.sizeBytes,
            ),
        )
        return JavaScriptWorkspaceWriteResult(record.relativePath, record.sizeBytes, record.mimeType)
    }

    /** Read a bounded UTF-8 workspace file after applying the same hardened path checks. */
    override fun read(
        relativePath: String,
        maxBytes: Long,
    ): JavaScriptWorkspaceReadResult {
        require(maxBytes > 0) { "Workspace read limit must be positive" }
        val path = resolveExistingFile(relativePath)
        if (Files.size(path) > maxBytes) throw JavaScriptWorkspaceReadLimitExceededException()
        val limit = min(maxBytes, Int.MAX_VALUE.toLong() - 1).toInt()
        val bytes = Files.newInputStream(path).use { input -> input.readNBytes(limit + 1) }
        if (bytes.size > limit) throw JavaScriptWorkspaceReadLimitExceededException()
        val root = workspaceFiles.workspaceRoot().toAbsolutePath().normalize()
        val normalized = root.relativize(path).toString().replace('\\', '/')
        return JavaScriptWorkspaceReadResult(normalized, bytes.toString(StandardCharsets.UTF_8), bytes.size.toLong())
    }

    /** Delete a workspace file and remove its persisted metadata when present. */
    override fun delete(relativePath: String): JavaScriptWorkspaceDeleteResult {
        val path = resolveExistingFile(relativePath)
        val root = workspaceFiles.workspaceRoot().toAbsolutePath().normalize()
        val normalized = root.relativize(path).toString().replace('\\', '/')
        val record = workspaceFiles.listFiles().firstOrNull { it.relativePath.replace('\\', '/') == normalized }
        val deleted =
            if (record != null) {
                workspaceFiles.deleteFile(record.id)
            } else {
                path.deleteIfExists()
            }
        if (record == null && deleted) {
            activityEvents.publish(WorkspaceFileActivity("Workspace file deleted by JavaScript: $normalized.", normalized, "delete"))
        }
        return JavaScriptWorkspaceDeleteResult(normalized, deleted)
    }

    private fun resolveExistingFile(relativePath: String): java.nio.file.Path {
        val path = resolvePath(relativePath)
        require(Files.exists(path) && Files.isRegularFile(path)) { "Workspace file does not exist" }
        require(!path.isSymbolicLink()) { "Workspace symbolic links are not accessible" }
        return path
    }

    private fun createSafeDirectories(
        root: Path,
        parent: Path,
        realRoot: Path,
    ) {
        val relativeParent = root.relativize(parent)
        var current = root
        relativeParent.forEach { component ->
            current = current.resolve(component.toString())
            when {
                Files.exists(current, LinkOption.NOFOLLOW_LINKS) -> {
                    require(!current.isSymbolicLink()) { "Workspace symbolic links are not writable" }
                    require(Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) { "Workspace parent is not a directory" }
                    require(current.toRealPath().startsWith(realRoot)) { "Workspace path escapes the workspace" }
                }
                else -> {
                    Files.createDirectory(current)
                    require(!current.isSymbolicLink()) { "Workspace symbolic links are not writable" }
                    require(current.toRealPath().startsWith(realRoot)) { "Workspace path escapes the workspace" }
                }
            }
        }
    }

    private fun resolvePath(relativePath: String): java.nio.file.Path {
        val normalized = relativePath.replace('\\', '/').trim()
        require(normalized.isNotBlank()) { "Workspace path must not be blank" }
        val requested =
            java.nio.file.Path
                .of(normalized)
        require(!requested.isAbsolute) { "Workspace path must be relative" }
        require(requested.nameCount > 0 && requested.none { it.toString() in setOf(".", "..") }) {
            "Workspace path traversal is not allowed"
        }
        val root = workspaceFiles.workspaceRoot().toAbsolutePath().normalize()
        val destination = root.resolve(normalized).normalize()
        require(destination.startsWith(root)) { "Workspace path escapes the workspace" }
        val realRoot = root.toRealPath()
        val realParent = (destination.parent ?: root).toRealPath()
        require(realParent.startsWith(realRoot)) { "Workspace path escapes the workspace" }
        return destination
    }
}
