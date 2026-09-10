package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.knowledge.DirectoryGrantRecord
import de.heckenmann.visualagent.knowledge.DirectoryGrantStore
import de.heckenmann.visualagent.protocol.DirectoryAccessMode
import de.heckenmann.visualagent.protocol.DirectoryGrantOrigin
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.UUID

/** Database-authoritative authorization service for explicitly granted server directories. */
@Service
class DirectoryGrantService(
    private val store: DirectoryGrantStore,
) {
    /** Lists grants without exposing their absolute roots to model-facing callers. */
    fun listGrants(): List<DirectoryGrant> = store.listDirectoryGrants().map(DirectoryGrantRecord::toDomain)

    /** Validates and canonicalizes an existing readable server directory. */
    fun inspectServerDirectory(absolutePath: String): Path {
        requireSafePathText(absolutePath)
        val requested = Path.of(absolutePath)
        require(requested.isAbsolute()) { "Directory path must be absolute" }
        val root = requested.toRealPath()
        require(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) { "Path is not a directory" }
        require(Files.isReadable(root)) { "Directory is not readable by the server" }
        return root
    }

    /** Persists one deduplicated server grant. */
    fun addServerGrant(
        absolutePath: String,
        displayName: String,
        mode: DirectoryAccessMode,
    ): DirectoryGrant {
        val root = inspectServerDirectory(absolutePath)
        require(displayName.isNotBlank()) { "Display name must not be blank" }
        require(store.getDirectoryGrantByCanonicalRoot(root.toString()) == null) { "Directory is already granted" }
        val now = Instant.now()
        return DirectoryGrant(
            id = "grant-${UUID.randomUUID()}",
            displayName = displayName.trim(),
            canonicalRoot = root.toString(),
            origin = DirectoryGrantOrigin.SERVER,
            mode = mode,
            clientBindingId = null,
            createdAt = now,
            updatedAt = now,
        ).also { store.saveDirectoryGrant(it.toRecord()) }
    }

    /** Updates only user-controlled grant metadata. */
    fun updateGrant(
        id: String,
        displayName: String,
        mode: DirectoryAccessMode,
    ): DirectoryGrant {
        require(displayName.isNotBlank()) { "Display name must not be blank" }
        val current = requireGrant(id)
        return current
            .copy(displayName = displayName.trim(), mode = mode, updatedAt = Instant.now())
            .also { store.saveDirectoryGrant(it.toRecord()) }
    }

    /** Revokes a grant immediately for every future authorization lookup. */
    fun removeGrant(id: String): Boolean = store.deleteDirectoryGrant(id)

    /** Returns whether a grant's owner currently confirms that it is available. */
    fun isAvailable(grant: DirectoryGrant): Boolean =
        grant.origin == DirectoryGrantOrigin.SERVER &&
            grant.canonicalRoot?.let { runCatching { inspectServerDirectory(it) }.isSuccess } == true

    /** Lists one directory using only an opaque grant ID and relative path. */
    fun list(
        grantId: String,
        relativePath: String,
    ): List<GrantedDirectoryEntry> {
        val target = authorizeExisting(grantId, relativePath, requireDirectory = true)
        val grant = requireGrant(grantId)
        return Files.list(target).use { children ->
            children
                .limit(MAX_ENTRIES.toLong())
                .map { child ->
                    val real = child.toRealPath()
                    requireContained(grant, real)
                    GrantedDirectoryEntry(
                        path = grantRelative(grant, real),
                        directory = Files.isDirectory(real, LinkOption.NOFOLLOW_LINKS),
                        sizeBytes = if (Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS)) Files.size(real) else null,
                    )
                }.toList()
                .sortedBy(GrantedDirectoryEntry::path)
        }
    }

    /** Reads bounded UTF-8 text from a regular file. */
    fun readText(
        grantId: String,
        relativePath: String,
    ): String {
        val file = authorizeExisting(grantId, relativePath, requireDirectory = false)
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) { "Path is not a regular file" }
        require(Files.size(file) <= MAX_TEXT_BYTES) { "File exceeds the ${MAX_TEXT_BYTES}-byte text limit" }
        return Files.readString(file, StandardCharsets.UTF_8)
    }

    /** Searches bounded UTF-8 files without following symbolic links. */
    fun search(
        grantId: String,
        query: String,
    ): List<GrantedDirectoryMatch> {
        require(query.isNotBlank()) { "Search query must not be blank" }
        val root = authorizeExisting(grantId, "", requireDirectory = true)
        val matches = mutableListOf<GrantedDirectoryMatch>()
        Files.walk(root).use { paths ->
            paths
                .filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
                .limit(MAX_SEARCH_FILES.toLong())
                .forEach { file ->
                    val authorized = authorizeExisting(grantId, grantRelative(requireGrant(grantId), file), requireDirectory = false)
                    if (Files.size(authorized) <= MAX_TEXT_BYTES) {
                        runCatching { Files.readAllLines(authorized, StandardCharsets.UTF_8) }
                            .getOrNull()
                            ?.forEachIndexed { index, line ->
                                if (matches.size < MAX_MATCHES && line.contains(query, ignoreCase = true)) {
                                    matches +=
                                        GrantedDirectoryMatch(grantRelative(requireGrant(grantId), authorized), index + 1, line.take(240))
                                }
                            }
                    }
                }
        }
        return matches
    }

    /** Writes bounded UTF-8 content after a final database and parent-containment check. */
    fun writeText(
        grantId: String,
        relativePath: String,
        content: String,
    ): String {
        require(content.toByteArray(StandardCharsets.UTF_8).size <= MAX_TEXT_BYTES) { "Content exceeds the write limit" }
        val target = authorizeMutationTarget(grantId, relativePath)
        Files.writeString(target, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        return grantRelative(requireGrant(grantId), target.toRealPath())
    }

    private fun authorizeExisting(
        grantId: String,
        relativePath: String,
        requireDirectory: Boolean,
    ): Path {
        val grant = requireServerGrant(grantId)
        val candidate = resolveRelative(grant, relativePath).toRealPath()
        requireContained(grant, candidate)
        if (requireDirectory) require(Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) { "Path is not a directory" }
        return candidate
    }

    private fun authorizeMutationTarget(
        grantId: String,
        relativePath: String,
    ): Path {
        val grant = requireServerGrant(grantId)
        require(grant.mode == DirectoryAccessMode.READ_WRITE) { "ACCESS_DENIED: directory is read-only" }
        val candidate = resolveRelative(grant, relativePath)
        val parent = requireNotNull(candidate.parent) { "A grant root cannot be replaced" }.toRealPath()
        requireContained(requireServerGrant(grantId), parent)
        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            requireContained(requireServerGrant(grantId), candidate.toRealPath())
        }
        return candidate
    }

    private fun resolveRelative(
        grant: DirectoryGrant,
        relativePath: String,
    ): Path {
        requireSafePathText(relativePath)
        val relative = Path.of(relativePath.ifBlank { "." })
        require(!relative.isAbsolute()) { "Grant paths must be relative" }
        require(relative.none { it.toString() == ".." }) { "Parent traversal is not allowed" }
        return Path.of(requireNotNull(grant.canonicalRoot)).resolve(relative).normalize()
    }

    private fun requireContained(
        grant: DirectoryGrant,
        candidate: Path,
    ) {
        val root = Path.of(requireNotNull(grant.canonicalRoot)).toRealPath()
        require(candidate.startsWith(root)) { "ACCESS_DENIED: path escapes granted directory" }
    }

    private fun requireGrant(id: String): DirectoryGrant =
        requireNotNull(store.getDirectoryGrant(id)) { "Unknown or revoked directory grant" }.toDomain()

    private fun requireServerGrant(id: String): DirectoryGrant =
        requireGrant(id).also {
            require(it.origin == DirectoryGrantOrigin.SERVER) { "Client directory is unavailable on this server connection" }
            require(isAvailable(it)) { "Granted directory is unavailable" }
        }

    private fun grantRelative(
        grant: DirectoryGrant,
        path: Path,
    ): String =
        Path
            .of(requireNotNull(grant.canonicalRoot))
            .toRealPath()
            .relativize(path.toRealPath())
            .toString()
            .replace('\\', '/')

    private companion object {
        const val MAX_ENTRIES = 1_000
        const val MAX_SEARCH_FILES = 1_000
        const val MAX_MATCHES = 200
        const val MAX_TEXT_BYTES = 262_144L
    }
}

private fun requireSafePathText(value: String) {
    require(value.none { it == '\u0000' || it.code < 0x20 }) { "Path contains control characters" }
}

private fun DirectoryGrantRecord.toDomain() =
    DirectoryGrant(
        id,
        displayName,
        canonicalRoot,
        DirectoryGrantOrigin.valueOf(origin),
        DirectoryAccessMode.valueOf(mode),
        clientBindingId,
        createdAt,
        updatedAt,
    )

private fun DirectoryGrant.toRecord() =
    DirectoryGrantRecord(id, displayName, canonicalRoot, origin.name, mode.name, clientBindingId, createdAt, updatedAt)
