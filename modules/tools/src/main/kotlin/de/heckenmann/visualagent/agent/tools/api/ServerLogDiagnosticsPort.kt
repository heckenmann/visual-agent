package de.heckenmann.visualagent.agent.tools.api

import reactor.core.publisher.Mono

/** One sanitized server log event retained in the bounded in-memory diagnostic buffer. */
data class ServerLogDiagnosticEntry(
    /** Monotonic sequence assigned when the event enters the buffer. */
    val sequence: Long,
    /** Event creation time as Unix epoch milliseconds. */
    val timestampEpochMillis: Long,
    /** Log level name. */
    val level: String,
    /** Logger name, bounded to a short diagnostic field. */
    val logger: String,
    /** Emitting thread name. */
    val thread: String,
    /** Message after secret redaction and length limiting. */
    val message: String,
    /** Optional correlation identifier from the logging context. */
    val correlationId: String?,
)

/** Bounded filters for searching recent sanitized server log events. */
data class ServerLogQuery(
    /** Optional exact level filter, such as `WARN` or `ERROR`. */
    val level: String? = null,
    /** Optional case-insensitive logger-name substring. */
    val loggerContains: String? = null,
    /** Optional case-insensitive message substring. */
    val query: String? = null,
    /** Optional exact correlation identifier. */
    val correlationId: String? = null,
    /** Maximum returned entries, restricted by the tool to 1 through 100. */
    val limit: Int = 50,
)

/** Read-only access to a bounded, redacted window of recent server log events. */
fun interface ServerLogDiagnosticsPort {
    /** Returns newest matching events first without reading arbitrary log files. */
    fun search(query: ServerLogQuery): Mono<List<ServerLogDiagnosticEntry>>
}
