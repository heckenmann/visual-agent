package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.protocol.DirectoryAccessMode
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/** Authorizes concrete server-owned grant paths after the service has verified a live grant. */
internal class DirectoryGrantServerAuthorization(
    private val requireServerGrant: (String) -> DirectoryGrant,
    private val pathPolicy: DirectoryGrantPathPolicy,
) {
    fun existing(
        grantId: String,
        relativePath: String,
        requireDirectory: Boolean,
    ): Path {
        val grant = requireServerGrant(grantId)
        val candidate = pathPolicy.resolveRelative(grant, relativePath).toRealPath()
        pathPolicy.requireContained(grant, candidate)
        if (requireDirectory) require(Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) { "Path is not a directory" }
        return candidate
    }

    fun mutationTarget(
        grantId: String,
        relativePath: String,
    ): Path {
        val grant = requireServerGrant(grantId)
        require(grant.mode == DirectoryAccessMode.READ_WRITE) { "ACCESS_DENIED: directory is read-only" }
        val candidate = pathPolicy.resolveRelative(grant, relativePath)
        pathPolicy.requireContained(grant, requireNotNull(candidate.parent) { "A grant root cannot be replaced" }.toRealPath())
        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) pathPolicy.requireContained(grant, candidate.toRealPath())
        return candidate
    }

    fun transferSource(
        grantId: String,
        relativePath: String,
        requireSourceWrite: Boolean,
    ): Path {
        val grant = requireServerGrant(grantId)
        if (requireSourceWrite) require(grant.mode == DirectoryAccessMode.READ_WRITE) { "ACCESS_DENIED: directory is read-only" }
        return existing(grantId, relativePath, false).also {
            require(Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS)) { "Path is not a regular file" }
        }
    }

    fun transferTarget(
        grantId: String,
        relativePath: String,
    ): Path =
        mutationTarget(
            grantId,
            relativePath,
        ).also { require(!Files.exists(it, LinkOption.NOFOLLOW_LINKS)) { "Target file already exists" } }
}
