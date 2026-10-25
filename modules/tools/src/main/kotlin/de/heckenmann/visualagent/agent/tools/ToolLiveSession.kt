package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.model.ToolLiveState
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.UUID

/** Owns bounded, ordered snapshots for exactly one execution, never a persistent state cache. */
internal class ToolLiveSession(
    private val scheduler: Scheduler = Schedulers.parallel(),
    private val publish: (ToolLiveState) -> Unit,
) : ToolLiveReporter,
    AutoCloseable {
    /** Unguessable identity used only for this active execution. */
    val executionId: String = UUID.randomUUID().toString()
    private var latest: ToolLiveState? = null
    private var publishedRevision = 0L
    private var closed = false
    private var ticker: Disposable? = null

    /** Starts bounded-rate publication; the first snapshot is always published immediately. */
    fun start() {
        ticker = Flux.interval(Duration.ofMillis(100), scheduler).subscribe { flush() }
    }

    override fun report(state: ToolLiveState) {
        synchronized(this) {
            if (closed) return
            val first = latest == null
            latest = bounded(state).copy(executionId = executionId, revision = (latest?.revision ?: 0) + 1, terminal = false)
            if (first) flush()
        }
    }

    /** Seals the latest snapshot and ignores all future producer updates. */
    fun finish(status: String? = null): ToolLiveState? =
        synchronized(this) {
            if (!closed) {
                closed = true
                ticker?.dispose()
                latest = latest?.let { it.copy(revision = it.revision + 1, terminal = true, status = status ?: it.status) }
            }
            latest
        }

    override fun close() {
        finish()
    }

    /** Returns the current immutable snapshot for a newly attached presentation subscriber. */
    fun snapshot(): ToolLiveState? = synchronized(this) { latest }

    private fun flush() {
        synchronized(this) {
            val value = latest ?: return
            if (closed || value.revision <= publishedRevision) return
            publishedRevision = value.revision
            publish(value)
        }
    }

    private fun bounded(state: ToolLiveState): ToolLiveState =
        state.copy(
            status = sanitizeLiveText(state.status).take(160),
            progress =
                state.progress
                    ?.takeIf { it.current >= 0 && (it.total?.let { total -> total >= 0 } ?: true) }
                    ?.let {
                        it.copy(
                            unit = sanitizeLiveText(it.unit).take(40),
                            perSecond =
                                it.perSecond?.takeIf { rate ->
                                    rate.isFinite() &&
                                        rate >= 0
                                },
                        )
                    },
            logs = state.logs.takeLast(40).map { sanitizeLiveText(it).take(500) },
            series =
                state.series.take(4).map {
                    it.copy(
                        label = sanitizeLiveText(it.label).take(160),
                        unit = sanitizeLiveText(it.unit).take(40),
                        values = it.values.takeLast(128).filter(Double::isFinite),
                    )
                },
            table =
                state.table?.let { table ->
                    table.copy(
                        columns = table.columns.take(8).map { sanitizeLiveText(it).take(80) },
                        rows =
                            table.rows.take(40).map { row ->
                                row.take(8).map { sanitizeLiveText(it).take(160) }
                            },
                    )
                },
            artifacts =
                state.artifacts.take(8).map {
                    it.copy(
                        workspaceFileId = it.workspaceFileId.take(128),
                        label = sanitizeLiveText(it.label).take(160),
                        mimeType = it.mimeType.take(120),
                    )
                },
        )
}

private val liveSecret = Regex("(?i)(\\b(?:authorization|api[_-]?key|password|token|secret)\\s*[:=]\\s*)(?:Bearer\\s+)?[^\\s,;]+")
private val terminalEscape = Regex("\\u001B\\[[0-?]*[ -/]*[@-~]")

private fun sanitizeLiveText(value: String): String =
    liveSecret
        .replace(sanitizeToolInputForEvent(terminalEscape.replace(value, ""))) { "${it.groupValues[1]}[redacted]" }
        .filter { it >= ' ' || it == '\n' || it == '\t' }
