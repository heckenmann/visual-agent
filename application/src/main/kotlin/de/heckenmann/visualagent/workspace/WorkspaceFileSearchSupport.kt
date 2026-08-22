package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord
import kotlin.io.path.extension
import kotlin.io.path.readText

/** Searches workspace metadata and bounded text/PDF content. */
fun WorkspaceFileService.searchFiles(
    query: String,
    mimeType: String? = null,
): WorkspaceSearchResult {
    val normalized = query.trim().lowercase()
    require(normalized.isNotBlank()) { "Search query must not be blank" }
    val matches =
        listFiles()
            .asSequence()
            .filter { mimeType == null || it.mimeType.equals(mimeType, ignoreCase = true) }
            .mapNotNull { searchRecord(it, normalized) }
            .take(MAX_SEARCH_RESULTS)
            .toList()
    return WorkspaceSearchResult(query = query, matches = matches)
}

internal fun WorkspaceFileService.searchRecord(
    record: WorkspaceFileRecord,
    query: String,
): WorkspaceSearchMatch? {
    val metadataHaystack =
        listOf(record.relativePath, record.originalName, record.mimeType, record.sha256)
            .joinToString("\n")
            .lowercase()
    if (metadataHaystack.contains(query)) {
        return WorkspaceSearchMatch(record, "metadata", record.relativePath)
    }
    val path = runCatching { resolveManagedPath(record.relativePath) }.getOrNull() ?: return null
    val text =
        when {
            record.mimeType == "application/pdf" -> runCatching { extractPdfText(record).text }.getOrNull()
            record.mimeType.startsWith("text/") || path.extension.lowercase() in WorkspaceFilePaths.TEXT_EXTENSIONS ->
                runCatching { path.readText(Charsets.UTF_8).take(MAX_TEXT_CHARS) }.getOrNull()
            else -> null
        } ?: return null
    val index = text.lowercase().indexOf(query)
    if (index < 0) return null
    return WorkspaceSearchMatch(record, "content", text.snippet(index))
}

private const val MAX_SEARCH_RESULTS = 50

private const val MAX_TEXT_CHARS = 120_000
