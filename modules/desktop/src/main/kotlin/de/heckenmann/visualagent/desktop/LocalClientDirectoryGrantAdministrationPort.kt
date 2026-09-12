package de.heckenmann.visualagent.desktop

import de.heckenmann.visualagent.protocol.ClientDirectoryGrantAdministrationPort
import de.heckenmann.visualagent.protocol.ClientDirectoryCapabilityRegistration
import de.heckenmann.visualagent.protocol.ClientDirectoryEntry
import de.heckenmann.visualagent.protocol.ClientDirectoryFileAccess
import de.heckenmann.visualagent.protocol.ClientDirectoryMatch
import de.heckenmann.visualagent.protocol.DirectoryAccessMode
import de.heckenmann.visualagent.workspace.ClientDirectoryCapabilityRegistry
import de.heckenmann.visualagent.workspace.VisualAgentProtectedDirectoryPolicy
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.prefs.Preferences

/**
 * Owns client-local directory roots selected by the user and exposes only capability-scoped access
 * to the embedded application server.
 */
internal class LocalClientDirectoryGrantAdministrationPort(
    private val capabilities: ClientDirectoryCapabilityRegistry,
    private val protectedDirectories: VisualAgentProtectedDirectoryPolicy,
    private val clientId: String = persistentClientId(),
) : ClientDirectoryGrantAdministrationPort {
    override fun prepareDirectoryGrant(
        absolutePath: String,
        mode: DirectoryAccessMode,
    ): ClientDirectoryCapabilityRegistration = registerDirectoryGrant("grant-${UUID.randomUUID()}", absolutePath, mode)

    override fun reactivateDirectoryGrant(
        grantId: String,
        absolutePath: String,
        mode: DirectoryAccessMode,
    ): ClientDirectoryCapabilityRegistration = registerDirectoryGrant(grantId, absolutePath, mode)

    private fun registerDirectoryGrant(
        grantId: String,
        absolutePath: String,
        mode: DirectoryAccessMode,
    ): ClientDirectoryCapabilityRegistration {
        val root = canonicalDirectory(absolutePath).also(protectedDirectories::requireGrantable)
        val registration =
            ClientDirectoryCapabilityRegistration(
                grantId = grantId,
                clientId = clientId,
                capabilityId = UUID.randomUUID().toString(),
            )
        capabilities.register(registration, LocalClientDirectoryFileAccess(root, mode))
        return registration
    }

    override fun revokeDirectoryGrant(grantId: String) {
        capabilities.revoke(grantId)
    }

    private companion object {
        const val PREFERENCES_NODE = "de.heckenmann.visualagent.desktop"
        const val CLIENT_ID_KEY = "client.id"

        fun persistentClientId(): String {
            val preferences = Preferences.userRoot().node(PREFERENCES_NODE)
            return preferences.get(CLIENT_ID_KEY, null) ?: UUID.randomUUID().toString().also { preferences.put(CLIENT_ID_KEY, it) }
        }

        fun canonicalDirectory(absolutePath: String): Path {
            require(absolutePath.none { it == '\u0000' || it.code < 0x20 }) { "Path contains control characters" }
            val requested = Path.of(absolutePath)
            require(requested.isAbsolute()) { "Client directory path must be absolute" }
            val root = requested.toRealPath()
            require(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) { "Client path is not a directory" }
            require(Files.isReadable(root)) { "Client directory is not readable" }
            return root
        }
    }
}

/** Performs filesystem operations only beneath one client-owned canonical root. */
private class LocalClientDirectoryFileAccess(
    private val root: Path,
    private val mode: DirectoryAccessMode,
) : ClientDirectoryFileAccess {
    override fun isAvailable(): Boolean = runCatching { Files.isDirectory(root) && Files.isReadable(root) }.getOrDefault(false)

    override fun list(relativePath: String): List<ClientDirectoryEntry> {
        val directory = existing(relativePath, requireDirectory = true)
        return Files.list(directory).use { paths ->
            paths
                .limit(MAX_ENTRIES.toLong())
                .map { child ->
                    val real = child.toRealPath()
                    requireContained(real)
                    ClientDirectoryEntry(
                        path = relative(real),
                        directory = Files.isDirectory(real, LinkOption.NOFOLLOW_LINKS),
                        sizeBytes = if (Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS)) Files.size(real) else null,
                    )
                }.toList()
                .sortedBy(ClientDirectoryEntry::path)
        }
    }

    override fun readText(relativePath: String): String {
        val file = existing(relativePath, requireDirectory = false)
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) { "Path is not a regular file" }
        require(Files.size(file) <= MAX_TEXT_BYTES) { "File exceeds the ${MAX_TEXT_BYTES}-byte text limit" }
        return Files.readString(file, StandardCharsets.UTF_8)
    }

    override fun search(
        query: String,
        path: String,
    ): List<ClientDirectoryMatch> {
        require(query.isNotBlank()) { "Search query must not be blank" }
        val searchRoot = existing(path, requireDirectory = true)
        val matches = mutableListOf<ClientDirectoryMatch>()
        Files.walk(searchRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
                .limit(MAX_SEARCH_FILES.toLong())
                .forEach { file ->
                    val real = file.toRealPath()
                    requireContained(real)
                    if (Files.size(real) <= MAX_TEXT_BYTES) {
                        runCatching { Files.readAllLines(real, StandardCharsets.UTF_8) }
                            .getOrNull()
                            ?.forEachIndexed { index, line ->
                                if (matches.size < MAX_MATCHES && line.contains(query, ignoreCase = true)) {
                                    matches += ClientDirectoryMatch(relative(real), index + 1, line.take(MAX_SNIPPET_LENGTH))
                                }
                            }
                    }
                }
        }
        return matches
    }

    override fun glob(
        path: String,
        pattern: String,
    ): List<ClientDirectoryEntry> {
        require(pattern.isNotBlank()) { "Glob pattern must not be blank" }
        val scope = existing(path, requireDirectory = true)
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
        return Files.walk(scope).use { paths ->
            paths
                .filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
                .map { it.toRealPath() }
                .peek(::requireContained)
                .filter { matcher.matches(scope.relativize(it)) }
                .limit(MAX_GLOB_MATCHES.toLong())
                .map { file -> ClientDirectoryEntry(relative(file), directory = false, sizeBytes = Files.size(file)) }
                .toList()
        }
    }

    override fun writeText(
        relativePath: String,
        content: String,
    ): String {
        require(mode == DirectoryAccessMode.READ_WRITE) { "ACCESS_DENIED: directory is read-only" }
        require(content.toByteArray(StandardCharsets.UTF_8).size <= MAX_TEXT_BYTES) { "Content exceeds the write limit" }
        val target = mutationTarget(relativePath)
        Files.writeString(target, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        return relative(target.toRealPath())
    }

    override fun createDirectory(relativePath: String): String {
        require(mode == DirectoryAccessMode.READ_WRITE) { "ACCESS_DENIED: directory is read-only" }
        require(relativePath.isNotBlank()) { "Directory path must not be blank" }
        val target = mutationTarget(relativePath)
        Files.createDirectories(target)
        return relative(target.toRealPath())
    }

    override fun readBytes(
        relativePath: String,
        maximumBytes: Long,
    ): ByteArray {
        require(maximumBytes > 0) { "maximumBytes must be positive" }
        require(maximumBytes <= Int.MAX_VALUE - 1L) { "maximumBytes is too large" }
        val file = existing(relativePath, requireDirectory = false)
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) { "Path is not a regular file" }
        require(Files.size(file) <= maximumBytes) { "File exceeds the $maximumBytes-byte limit" }
        return Files.newInputStream(file).use { input -> input.readNBytes(maximumBytes.toInt() + 1) }.also {
            require(it.size.toLong() <= maximumBytes) { "File exceeds the $maximumBytes-byte limit" }
        }
    }

    override fun delete(
        relativePath: String,
        recursive: Boolean,
    ) {
        require(mode == DirectoryAccessMode.READ_WRITE) { "ACCESS_DENIED: directory is read-only" }
        require(relativePath.isNotBlank()) { "A grant root cannot be deleted" }
        val target = existing(relativePath, requireDirectory = false)
        if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            require(recursive || Files.list(target).use { !it.findAny().isPresent }) { "Directory is not empty" }
            if (recursive) {
                Files.walk(target).use { paths -> paths.sorted(java.util.Comparator.reverseOrder()).forEach(Files::delete) }
            } else {
                Files.delete(target)
            }
        } else {
            Files.delete(target)
        }
    }

    private fun existing(
        relativePath: String,
        requireDirectory: Boolean,
    ): Path {
        val candidate = resolve(relativePath).toRealPath()
        requireContained(candidate)
        if (requireDirectory) require(Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) { "Path is not a directory" }
        return candidate
    }

    private fun mutationTarget(relativePath: String): Path {
        val candidate = resolve(relativePath)
        val parent = requireNotNull(candidate.parent) { "A grant root cannot be replaced" }.toRealPath()
        requireContained(parent)
        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) requireContained(candidate.toRealPath())
        return candidate
    }

    private fun resolve(relativePath: String): Path {
        require(relativePath.none { it == '\u0000' || it.code < 0x20 }) { "Path contains control characters" }
        val relative = Path.of(relativePath.ifBlank { "." })
        require(!relative.isAbsolute()) { "Grant paths must be relative" }
        require(relative.none { it.toString() == ".." }) { "Parent traversal is not allowed" }
        return root.resolve(relative).normalize()
    }

    private fun requireContained(candidate: Path) {
        require(candidate.startsWith(root)) { "ACCESS_DENIED: path escapes granted directory" }
    }

    private fun relative(path: Path): String = root.relativize(path).toString().replace('\\', '/')

    private companion object {
        const val MAX_ENTRIES = 1_000
        const val MAX_SEARCH_FILES = 1_000
        const val MAX_MATCHES = 200
        const val MAX_GLOB_MATCHES = 500
        const val MAX_TEXT_BYTES = 262_144L
        const val MAX_SNIPPET_LENGTH = 240
    }
}
