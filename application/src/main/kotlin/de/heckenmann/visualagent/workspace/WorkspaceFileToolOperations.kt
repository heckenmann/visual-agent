package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import java.nio.file.FileSystems
import java.nio.file.Path

/** One line matched by a bounded workspace text search. */
internal data class WorkspaceTextMatch(
    val path: String,
    val line: Int,
    val snippet: String,
)

/** Finds managed files matching [pattern] below the workspace-relative [path]. */
internal fun WorkspaceFileService.globFiles(
    path: String,
    pattern: String,
): List<WorkspaceFileRecord> {
    require(pattern.isNotBlank()) { "Glob pattern must not be blank" }
    val scope = normalizedToolPath(path)
    val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
    return listFiles()
        .asSequence()
        .filter { it.isBelow(scope) }
        .filter { record -> matcher.matches(Path.of(record.relativePath.removePrefix("$scope/").ifBlank { record.relativePath })) }
        .take(MAX_GLOB_MATCHES)
        .toList()
}

/** Searches bounded managed text files below [path] line by line. */
internal fun WorkspaceFileService.grepText(
    query: String,
    path: String,
): List<WorkspaceTextMatch> {
    require(query.isNotBlank()) { "Search query must not be blank" }
    val scope = normalizedToolPath(path)
    val matches = mutableListOf<WorkspaceTextMatch>()
    listFiles()
        .asSequence()
        .filter { it.isBelow(scope) }
        .take(MAX_SEARCH_FILES)
        .forEach { record ->
            runCatching { readText(record) }
                .getOrNull()
                ?.lineSequence()
                ?.forEachIndexed { index, line ->
                    if (matches.size < MAX_TEXT_MATCHES && line.contains(query, ignoreCase = true)) {
                        matches += WorkspaceTextMatch(record.relativePath, index + 1, line.take(MAX_SNIPPET_LENGTH))
                    }
                }
        }
    return matches
}

/** Replaces exactly one [oldText] occurrence in a managed workspace file. */
internal fun WorkspaceFileService.replaceText(
    path: String,
    oldText: String,
    newText: String,
): WorkspaceFileRecord {
    require(oldText.isNotEmpty()) { "oldText must not be empty" }
    val record = requireFile(null, path)
    val current = readText(record)
    val firstIndex = current.indexOf(oldText)
    require(firstIndex >= 0) { "oldText not found" }
    require(current.indexOf(oldText, firstIndex + oldText.length) < 0) { "oldText must occur exactly once" }
    return writeText(record.relativePath, current.replaceRange(firstIndex, firstIndex + oldText.length, newText))
}

private fun normalizedToolPath(path: String): String {
    val normalized = WorkspaceFilePaths.normalizeRelativePath(path)
    val segments = if (normalized.isBlank()) emptyList() else normalized.split('/')
    require(segments.none { it.isBlank() || it == "." || it == ".." }) { "Workspace path must be a relative directory" }
    return normalized
}

private fun WorkspaceFileRecord.isBelow(scope: String): Boolean =
    scope.isBlank() || relativePath == scope || relativePath.startsWith("$scope/")

private const val MAX_GLOB_MATCHES = 500
private const val MAX_SEARCH_FILES = 1_000
private const val MAX_TEXT_MATCHES = 200
private const val MAX_SNIPPET_LENGTH = 240
