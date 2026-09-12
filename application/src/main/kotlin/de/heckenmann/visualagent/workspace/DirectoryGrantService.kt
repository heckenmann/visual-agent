package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.knowledge.DirectoryGrantRecord
import de.heckenmann.visualagent.knowledge.DirectoryGrantStore
import de.heckenmann.visualagent.protocol.ClientDirectoryCapabilityRegistration
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
    private val clientCapabilities: ClientDirectoryCapabilityRegistry,
    private val protectedDirectories: VisualAgentProtectedDirectoryPolicy,
) {
    private val pathPolicy = DirectoryGrantPathPolicy()
    private val serverAuthorization = DirectoryGrantServerAuthorization(::requireServerGrant, pathPolicy)
    private val serverReads = DirectoryGrantServerReadOperations(::authorizeExisting, ::grantRelative)
    private val transfers =
        DirectoryGrantTransferOperations(
            authorizeSource = ::authorizeTransferSource,
            authorizeTarget = ::authorizeTransferTarget,
            targetRelativePath = { grantId, path -> grantRelative(requireServerGrant(grantId), path) },
        )

    /** Lists grants without exposing their absolute roots to model-facing callers. */
    fun listGrants(): List<DirectoryGrant> = store.listDirectoryGrants().map(DirectoryGrantRecord::toDomain)

    /** Validates and canonicalizes an existing readable server directory. */
    fun inspectServerDirectory(absolutePath: String): Path {
        pathPolicy.requireSafePathText(absolutePath)
        val requested = Path.of(absolutePath)
        require(requested.isAbsolute()) { "Directory path must be absolute" }
        val root = requested.toRealPath()
        require(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) { "Path is not a directory" }
        require(Files.isReadable(root)) { "Directory is not readable by the server" }
        protectedDirectories.requireGrantable(root)
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
            ownerClientId = null,
            createdAt = now,
            updatedAt = now,
        ).also { store.saveDirectoryGrant(it.toRecord()) }
    }

    /** Persists one client-owned grant only after the owning client registered its live capability. */
    fun addClientGrant(
        registration: ClientDirectoryCapabilityRegistration,
        displayName: String,
        mode: DirectoryAccessMode,
    ): DirectoryGrant {
        require(displayName.isNotBlank()) { "Display name must not be blank" }
        require(clientCapabilities.isAvailable(registration)) { "CLIENT_DISCONNECTED: directory owner is unavailable" }
        require(store.getDirectoryGrant(registration.grantId) == null) { "Directory capability is already granted" }
        val now = Instant.now()
        return DirectoryGrant(
            id = registration.grantId,
            displayName = displayName.trim(),
            canonicalRoot = null,
            origin = DirectoryGrantOrigin.CLIENT,
            mode = mode,
            ownerClientId = registration.clientId,
            createdAt = now,
            updatedAt = now,
        ).also { store.saveDirectoryGrant(it.toRecord()) }
    }

    /** Reattaches an existing client grant to a newly negotiated owner capability. */
    fun reactivateClientGrant(registration: ClientDirectoryCapabilityRegistration): DirectoryGrant {
        require(clientCapabilities.isAvailable(registration)) { "CLIENT_DISCONNECTED: directory owner is unavailable" }
        val existing = requireGrant(registration.grantId)
        require(existing.origin == DirectoryGrantOrigin.CLIENT) { "ACCESS_DENIED: grant is not client-owned" }
        require(existing.ownerClientId == registration.clientId) { "ACCESS_DENIED: directory belongs to another client" }
        return existing.copy(updatedAt = Instant.now()).also { store.saveDirectoryGrant(it.toRecord()) }
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
    fun removeGrant(id: String): Boolean {
        val grant = store.getDirectoryGrant(id)?.toDomain() ?: return false
        val deleted = store.deleteDirectoryGrant(id)
        if (deleted && grant.origin == DirectoryGrantOrigin.CLIENT) clientCapabilities.revoke(id)
        return deleted
    }

    /** Returns whether a grant's owner currently confirms that it is available. */
    fun isAvailable(grant: DirectoryGrant): Boolean =
        when (grant.origin) {
            DirectoryGrantOrigin.SERVER -> grant.canonicalRoot?.let { runCatching { inspectServerDirectory(it) }.isSuccess } == true
            DirectoryGrantOrigin.CLIENT -> grant.ownerClientId?.let { clientCapabilities.isAvailable(grant.id, it) } == true
        }

    /** Lists one directory using only an opaque grant ID and relative path. */
    fun list(
        grantId: String,
        relativePath: String,
    ): List<GrantedDirectoryEntry> {
        val grant = requireGrant(grantId)
        if (grant.origin == DirectoryGrantOrigin.CLIENT) {
            return clientAccess(grant).list(relativePath).map { GrantedDirectoryEntry(it.path, it.directory, it.sizeBytes) }
        }
        return serverReads.list(grant, grantId, relativePath)
    }

    /** Reads bounded UTF-8 text from a regular file. */
    fun readText(
        grantId: String,
        relativePath: String,
    ): String {
        val grant = requireGrant(grantId)
        if (grant.origin == DirectoryGrantOrigin.CLIENT) return clientAccess(grant).readText(relativePath)
        return serverReads.readText(grantId, relativePath)
    }

    /** Reads a bounded binary file using its persisted opaque grant identity. */
    fun readBytes(
        grantId: String,
        relativePath: String,
        maximumBytes: Long,
    ): ByteArray {
        require(maximumBytes > 0) { "maximumBytes must be positive" }
        require(maximumBytes <= Int.MAX_VALUE - 1L) { "maximumBytes is too large" }
        val grant = requireGrant(grantId)
        if (grant.origin == DirectoryGrantOrigin.CLIENT) return clientAccess(grant).readBytes(relativePath, maximumBytes)
        return serverReads.readBytes(grantId, relativePath, maximumBytes)
    }

    /** Searches bounded UTF-8 files without following symbolic links. */
    fun search(
        grantId: String,
        query: String,
        relativePath: String = "",
    ): List<GrantedDirectoryMatch> {
        require(query.isNotBlank()) { "Search query must not be blank" }
        val clientGrant = requireGrant(grantId)
        if (clientGrant.origin == DirectoryGrantOrigin.CLIENT) {
            return clientAccess(clientGrant).search(query, relativePath).map { GrantedDirectoryMatch(it.path, it.line, it.snippet) }
        }
        return serverReads.search(clientGrant, grantId, query, relativePath)
    }

    /** Finds bounded regular-file entries matching [pattern] below a grant-relative directory. */
    fun glob(
        grantId: String,
        relativePath: String,
        pattern: String,
    ): List<GrantedDirectoryEntry> {
        require(pattern.isNotBlank()) { "Glob pattern must not be blank" }
        val grant = requireGrant(grantId)
        if (grant.origin == DirectoryGrantOrigin.CLIENT) {
            return clientAccess(grant).glob(relativePath, pattern).map { GrantedDirectoryEntry(it.path, it.directory, it.sizeBytes) }
        }
        return serverReads.glob(grant, grantId, relativePath, pattern)
    }

    /** Writes bounded UTF-8 content after a final database and parent-containment check. */
    fun writeText(
        grantId: String,
        relativePath: String,
        content: String,
    ): String {
        require(content.toByteArray(StandardCharsets.UTF_8).size <= MAX_TEXT_BYTES) { "Content exceeds the write limit" }
        val grant = requireGrant(grantId)
        if (grant.origin == DirectoryGrantOrigin.CLIENT) {
            require(grant.mode == DirectoryAccessMode.READ_WRITE) { "ACCESS_DENIED: directory is read-only" }
            return clientAccess(grant).writeText(relativePath, content)
        }
        val target = authorizeMutationTarget(grantId, relativePath)
        Files.writeString(target, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        return grantRelative(requireGrant(grantId), target.toRealPath())
    }

    /** Replaces exactly one [oldText] occurrence in a grant-relative text file. */
    fun editText(
        grantId: String,
        relativePath: String,
        oldText: String,
        newText: String,
    ): String {
        require(oldText.isNotEmpty()) { "oldText must not be empty" }
        val current = readText(grantId, relativePath)
        val firstIndex = current.indexOf(oldText)
        require(firstIndex >= 0) { "oldText not found" }
        require(current.indexOf(oldText, firstIndex + oldText.length) < 0) { "oldText must occur exactly once" }
        return writeText(grantId, relativePath, current.replaceRange(firstIndex, firstIndex + oldText.length, newText))
    }

    /** Creates a grant-relative directory after a final owner-side authorization check. */
    fun createDirectory(
        grantId: String,
        relativePath: String,
    ): String {
        require(relativePath.isNotBlank()) { "Directory path must not be blank" }
        val grant = requireGrant(grantId)
        require(grant.mode == DirectoryAccessMode.READ_WRITE) { "ACCESS_DENIED: directory is read-only" }
        if (grant.origin == DirectoryGrantOrigin.CLIENT) return clientAccess(grant).createDirectory(relativePath)
        val target = authorizeMutationTarget(grantId, relativePath)
        Files.createDirectories(target)
        return grantRelative(requireGrant(grantId), target.toRealPath())
    }

    /** Deletes a file or directory after a final owner-side authorization check. */
    fun delete(
        grantId: String,
        relativePath: String,
        recursive: Boolean,
    ) {
        require(relativePath.isNotBlank()) { "A grant root cannot be deleted" }
        val grant = requireGrant(grantId)
        require(grant.mode == DirectoryAccessMode.READ_WRITE) { "ACCESS_DENIED: directory is read-only" }
        if (grant.origin == DirectoryGrantOrigin.CLIENT) {
            clientAccess(grant).delete(relativePath, recursive)
            return
        }
        val target = authorizeExisting(grantId, relativePath, requireDirectory = false)
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

    /** Copies a regular file from [sourceGrantId] to [targetGrantId] after owner-side authorization. */
    fun copy(
        sourceGrantId: String,
        sourcePath: String,
        targetGrantId: String,
        targetPath: String,
    ): String = transfers.copy(sourceGrantId, sourcePath, targetGrantId, targetPath)

    /** Copies and verifies a regular file before deleting the source from [sourceGrantId]. */
    fun move(
        sourceGrantId: String,
        sourcePath: String,
        targetGrantId: String,
        targetPath: String,
    ): String = transfers.move(sourceGrantId, sourcePath, targetGrantId, targetPath)

    private fun authorizeExisting(
        grantId: String,
        relativePath: String,
        requireDirectory: Boolean,
    ): Path = serverAuthorization.existing(grantId, relativePath, requireDirectory)

    private fun authorizeMutationTarget(
        grantId: String,
        relativePath: String,
    ): Path = serverAuthorization.mutationTarget(grantId, relativePath)

    private fun authorizeTransferSource(
        grantId: String,
        relativePath: String,
        requireSourceWrite: Boolean,
    ): Path = serverAuthorization.transferSource(grantId, relativePath, requireSourceWrite)

    private fun authorizeTransferTarget(
        grantId: String,
        relativePath: String,
    ): Path = serverAuthorization.transferTarget(grantId, relativePath)

    private fun requireGrant(id: String): DirectoryGrant =
        requireNotNull(store.getDirectoryGrant(id)) { "Unknown or revoked directory grant" }.toDomain()

    private fun requireServerGrant(id: String): DirectoryGrant =
        requireGrant(id).also {
            require(it.origin == DirectoryGrantOrigin.SERVER) {
                "CROSS_ROOT_TRANSFER_UNAVAILABLE: client-owned directories require the file-exchange transport"
            }
            protectedDirectories.requireGrantable(Path.of(requireNotNull(it.canonicalRoot)))
            require(isAvailable(it)) { "Granted directory is unavailable" }
        }

    private fun clientAccess(grant: DirectoryGrant) = clientCapabilities.requireAccess(grant.id, requireNotNull(grant.ownerClientId))

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
        const val MAX_GLOB_MATCHES = 500
        const val MAX_TEXT_BYTES = 262_144L
    }
}

private fun DirectoryGrantRecord.toDomain() =
    DirectoryGrant(
        id,
        displayName,
        canonicalRoot,
        DirectoryGrantOrigin.valueOf(origin),
        DirectoryAccessMode.valueOf(mode),
        ownerClientId,
        createdAt,
        updatedAt,
    )

private fun DirectoryGrant.toRecord() =
    DirectoryGrantRecord(id, displayName, canonicalRoot, origin.name, mode.name, ownerClientId, createdAt, updatedAt)
