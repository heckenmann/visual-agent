package de.heckenmann.visualagent.workspace

import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/** Performs bounded server-owned directory reads after the grant service authorized their roots. */
internal class DirectoryGrantServerReadOperations(
    private val authorizeExisting: (String, String, Boolean) -> Path,
    private val relativePath: (DirectoryGrant, Path) -> String,
) {
    fun list(
        grant: DirectoryGrant,
        grantId: String,
        path: String,
    ): List<GrantedDirectoryEntry> {
        val target = authorizeExisting(grantId, path, true)
        return Files.list(target).use { children ->
            children
                .limit(MAX_ENTRIES.toLong())
                .map { child ->
                    val real = child.toRealPath()
                    GrantedDirectoryEntry(
                        relativePath(grant, real),
                        Files.isDirectory(real, LinkOption.NOFOLLOW_LINKS),
                        if (Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS)) Files.size(real) else null,
                    )
                }.toList()
                .sortedBy(GrantedDirectoryEntry::path)
        }
    }

    fun readText(
        grantId: String,
        path: String,
    ): String {
        val file = requireRegularFile(authorizeExisting(grantId, path, false))
        require(Files.size(file) <= MAX_TEXT_BYTES) { "File exceeds the $MAX_TEXT_BYTES-byte text limit" }
        return Files.readString(file, StandardCharsets.UTF_8)
    }

    fun readBytes(
        grantId: String,
        path: String,
        maximumBytes: Long,
    ): ByteArray {
        val file = requireRegularFile(authorizeExisting(grantId, path, false))
        require(Files.size(file) <= maximumBytes) { "File exceeds the $maximumBytes-byte limit" }
        return Files.newInputStream(file).use { it.readNBytes(maximumBytes.toInt() + 1) }.also {
            require(it.size.toLong() <= maximumBytes) { "File exceeds the $maximumBytes-byte limit" }
        }
    }

    fun search(
        grant: DirectoryGrant,
        grantId: String,
        query: String,
        path: String,
    ): List<GrantedDirectoryMatch> {
        val matches = mutableListOf<GrantedDirectoryMatch>()
        Files.walk(authorizeExisting(grantId, path, true)).use { paths ->
            paths.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }.limit(MAX_SEARCH_FILES.toLong()).forEach { file ->
                val authorized = authorizeExisting(grantId, relativePath(grant, file), false)
                if (Files.size(authorized) <=
                    MAX_TEXT_BYTES
                ) {
                    runCatching { Files.readAllLines(authorized, StandardCharsets.UTF_8) }.getOrNull()?.forEachIndexed { index, line ->
                        if (matches.size < MAX_MATCHES &&
                            line.contains(query, ignoreCase = true)
                        ) {
                            matches +=
                                GrantedDirectoryMatch(relativePath(grant, authorized), index + 1, line.take(240))
                        }
                    }
                }
            }
        }
        return matches
    }

    fun glob(
        grant: DirectoryGrant,
        grantId: String,
        path: String,
        pattern: String,
    ): List<GrantedDirectoryEntry> {
        val scope = authorizeExisting(grantId, path, true)
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
        return Files.walk(scope).use { paths ->
            paths
                .filter {
                    Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS)
                }.map { it.toRealPath() }
                .filter { matcher.matches(scope.relativize(it)) }
                .limit(MAX_GLOB_MATCHES.toLong())
                .map {
                    GrantedDirectoryEntry(relativePath(grant, it), false, Files.size(it))
                }.toList()
        }
    }

    private fun requireRegularFile(path: Path): Path =
        path.also {
            require(Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS)) { "Path is not a regular file" }
        }

    private companion object {
        const val MAX_ENTRIES = 1_000
        const val MAX_SEARCH_FILES = 1_000
        const val MAX_MATCHES = 200
        const val MAX_GLOB_MATCHES = 500
        const val MAX_TEXT_BYTES = 262_144L
    }
}
