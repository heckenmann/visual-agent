package de.heckenmann.visualagent.workspace

import java.nio.file.Path

/** Validates opaque grant-relative paths and enforces real-path containment. */
internal class DirectoryGrantPathPolicy {
    fun resolveRelative(
        grant: DirectoryGrant,
        relativePath: String,
    ): Path {
        requireSafePathText(relativePath)
        val relative = Path.of(relativePath.ifBlank { "." })
        require(!relative.isAbsolute()) { "Grant paths must be relative" }
        require(relative.none { it.toString() == ".." }) { "Parent traversal is not allowed" }
        return Path.of(requireNotNull(grant.canonicalRoot)).resolve(relative).normalize()
    }

    fun requireContained(
        grant: DirectoryGrant,
        candidate: Path,
    ) {
        val root = Path.of(requireNotNull(grant.canonicalRoot)).toRealPath()
        require(candidate.startsWith(root)) { "ACCESS_DENIED: path escapes granted directory" }
    }

    fun requireSafePathText(value: String) {
        require(value.none { it == '\u0000' || it.code < 0x20 }) { "Path contains control characters" }
    }
}
