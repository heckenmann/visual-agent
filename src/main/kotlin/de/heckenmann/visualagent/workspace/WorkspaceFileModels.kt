package de.heckenmann.visualagent.workspace

import de.heckenmann.visualagent.knowledge.WorkspaceFileRecord

/** Search result for workspace files. */
data class WorkspaceSearchResult(
    val query: String,
    val matches: List<WorkspaceSearchMatch>,
)

/** One workspace search hit. */
data class WorkspaceSearchMatch(
    val record: WorkspaceFileRecord,
    val matchType: String,
    val snippet: String,
)

/** Filesystem/DB reconciliation report. */
data class WorkspaceSyncResult(
    val added: Int,
    val updated: Int,
    val removed: Int,
    val total: Int,
)

/** Extracted text result with cache state. */
data class WorkspaceFileText(
    val text: String,
    val cached: Boolean,
)

/** Image metadata returned to tools and UI consumers. */
data class WorkspaceImageInfo(
    val width: Int,
    val height: Int,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
)

/** Bounded base64 image payload. */
data class WorkspaceImageBytes(
    val mimeType: String,
    val base64: String,
)
