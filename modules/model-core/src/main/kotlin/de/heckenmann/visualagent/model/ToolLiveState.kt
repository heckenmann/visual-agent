package de.heckenmann.visualagent.model

import kotlinx.serialization.Serializable

/**
 * Complete semantic execution snapshot, not an append command.
 * Status, deadline and progress replace their previous values; only explicit log entries form a log.
 * Log producers supply their retained entries in order, rather than converting status refreshes into logs.
 */
@Serializable
data class ToolLiveState(
    val executionId: String,
    val revision: Long,
    val status: String,
    val progress: ToolLiveProgress? = null,
    val deadlineEpochMillis: Long? = null,
    val logs: List<String> = emptyList(),
    val table: ToolLiveTable? = null,
    val artifacts: List<ToolLiveArtifact> = emptyList(),
    val terminal: Boolean = false,
    val series: List<ToolLiveSeries> = emptyList(),
)

/** Measured progress, optionally including a known total and transfer rate. */
@Serializable
data class ToolLiveProgress(
    val current: Long,
    val total: Long? = null,
    val unit: String = "",
    val perSecond: Double? = null,
)

/** Bounded tabular values provided by a running tool. */
@Serializable
data class ToolLiveTable(
    val columns: List<String>,
    val rows: List<List<String>>,
)

/** Server-managed artifact reference, never a client-fetched URL or embedded byte payload. */
@Serializable
data class ToolLiveArtifact(
    val workspaceFileId: String,
    val label: String,
    val mimeType: String,
)

/** Ordered measured samples; renderers may show a bounded numeric table without inventing data. */
@Serializable
data class ToolLiveSeries(
    val label: String,
    val values: List<Double>,
    val unit: String = "",
)
