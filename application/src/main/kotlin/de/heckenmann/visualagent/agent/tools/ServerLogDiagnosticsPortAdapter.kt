package de.heckenmann.visualagent.agent.tools

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import de.heckenmann.visualagent.agent.tools.api.ServerLogDiagnosticEntry
import de.heckenmann.visualagent.agent.tools.api.ServerLogDiagnosticsPort
import de.heckenmann.visualagent.agent.tools.api.ServerLogQuery
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

/** Captures a bounded, sanitized window of Logback events for explicit model diagnostics. */
@Component
class ServerLogDiagnosticsPortAdapter :
    ServerLogDiagnosticsPort,
    InitializingBean,
    DisposableBean {
    private val buffer = ServerLogBuffer(MAX_RETAINED_ENTRIES)
    private var rootLogger: Logger? = null
    private var appender: ServerLogBufferAppender? = null

    /** Attaches the bounded diagnostic appender to the active Logback root logger. */
    override fun afterPropertiesSet() {
        val context =
            LoggerFactory.getILoggerFactory() as? LoggerContext
                ?: throw IllegalStateException("The configured logging backend does not support server log diagnostics.")
        val root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
        val logAppender =
            ServerLogBufferAppender(buffer).apply {
                setContext(context)
                start()
            }
        root.addAppender(logAppender)
        rootLogger = root
        appender = logAppender
    }

    /** Detaches the appender when the application context shuts down. */
    override fun destroy() {
        appender?.let { attached ->
            rootLogger?.detachAppender(attached)
            attached.stop()
        }
        appender = null
        rootLogger = null
    }

    /** Searches only the in-memory buffer and returns newest matching entries first. */
    override fun search(query: ServerLogQuery): Mono<List<ServerLogDiagnosticEntry>> = Mono.fromCallable { buffer.search(query) }

    private companion object {
        const val MAX_RETAINED_ENTRIES = 2_000
    }
}

/** Copies only bounded primitive log fields into the application-owned ring buffer. */
internal class ServerLogBufferAppender(
    private val buffer: ServerLogBuffer,
) : AppenderBase<ILoggingEvent>() {
    override fun append(eventObject: ILoggingEvent) {
        eventObject.prepareForDeferredProcessing()
        buffer.append(
            timestampEpochMillis = eventObject.timeStamp,
            level = eventObject.level.toString(),
            logger = eventObject.loggerName.orEmpty(),
            thread = eventObject.threadName.orEmpty(),
            message = eventObject.formattedMessage.orEmpty(),
            mdc = eventObject.mdcPropertyMap.orEmpty(),
        )
    }
}

/** Thread-safe fixed-capacity ring buffer that redacts sensitive values before retaining events. */
internal class ServerLogBuffer(
    private val capacity: Int,
) {
    private val sequence = AtomicLong()
    private val events = ArrayDeque<ServerLogDiagnosticEntry>(capacity)

    init {
        require(capacity > 0)
    }

    /** Sanitizes and appends one event, evicting the oldest event when the buffer is full. */
    @Synchronized
    fun append(
        timestampEpochMillis: Long,
        level: String,
        logger: String,
        thread: String,
        message: String,
        mdc: Map<String, String>,
    ) {
        val safeLogger = logger.take(MAX_LOGGER_LENGTH)
        val safeThread = thread.take(MAX_THREAD_LENGTH)
        val safeCorrelationId = CORRELATION_KEYS.firstNotNullOfOrNull { key -> mdc[key]?.takeIf(CORRELATION_VALUE::matches) }
        val entry =
            ServerLogDiagnosticEntry(
                sequence = sequence.incrementAndGet(),
                timestampEpochMillis = timestampEpochMillis,
                level = level.take(MAX_LEVEL_LENGTH),
                logger = safeLogger,
                thread = safeThread,
                message = ServerLogRedactor.redact(message.take(MAX_RAW_MESSAGE_LENGTH)).take(MAX_MESSAGE_LENGTH),
                correlationId = safeCorrelationId,
            )
        if (events.size == capacity) events.removeFirst()
        events.addLast(entry)
    }

    /** Returns filtered entries newest-first and caps the requested page size. */
    @Synchronized
    fun search(query: ServerLogQuery): List<ServerLogDiagnosticEntry> {
        val limit = query.limit.coerceIn(1, MAX_QUERY_RESULTS)
        val level = query.level?.uppercase()
        val loggerContains = query.loggerContains
        val messageContains = query.query
        val correlationId = query.correlationId
        return events
            .asSequence()
            .filter { level == null || it.level.equals(level, ignoreCase = true) }
            .filter { loggerContains == null || it.logger.contains(loggerContains, ignoreCase = true) }
            .filter { messageContains == null || it.message.contains(messageContains, ignoreCase = true) }
            .filter { correlationId == null || it.correlationId == correlationId }
            .toList()
            .asReversed()
            .take(limit)
    }

    private companion object {
        val CORRELATION_KEYS = listOf("correlationId", "correlation_id", "traceId", "trace_id", "requestId", "request_id")
        val CORRELATION_VALUE = Regex("[A-Za-z0-9._:-]{1,128}")
        const val MAX_LEVEL_LENGTH = 16
        const val MAX_LOGGER_LENGTH = 256
        const val MAX_THREAD_LENGTH = 128
        const val MAX_RAW_MESSAGE_LENGTH = 4_096
        const val MAX_MESSAGE_LENGTH = 2_048
        const val MAX_QUERY_RESULTS = 100
    }
}

/** Removes common credential formats before log text enters the diagnostic buffer. */
internal object ServerLogRedactor {
    private val bearerPattern = Regex("(?i)\\bBearer\\s+[^\\s,;]+")
    private val secretAssignmentPattern =
        Regex(
            "(?i)([\\\"']?(?:api[_ -]?key|access[_ -]?token|refresh[_ -]?token|password|passwd|secret|authorization|proxy-authorization)[\\\"']?\\s*[:=]\\s*[\\\"']?)([^\\s,;\\\"'&}]+)",
        )
    private val urlCredentialPattern = Regex("(?i)([a-z][a-z0-9+.-]*://)[^/@\\s]+:[^/@\\s]+@")
    private val secretQueryParameterPattern = Regex("(?i)([?&](?:api[_-]?key|key|access[_-]?token|token|password|secret)=)[^&#\\s]+")
    private val privateKeyPattern = Regex("-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z0-9 ]*PRIVATE KEY-----")
    private val unterminatedPrivateKeyPattern = Regex("-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----[\\s\\S]*")

    /** Redacts bearer credentials, common secret fields, URL passwords, and PEM private keys. */
    fun redact(value: String): String =
        value
            .replace(privateKeyPattern, "[REDACTED PRIVATE KEY]")
            .replace(unterminatedPrivateKeyPattern, "[REDACTED PRIVATE KEY]")
            .replace(bearerPattern, "Bearer [REDACTED]")
            .replace(secretAssignmentPattern) { match -> "${match.groupValues[1]}[REDACTED]" }
            .replace(urlCredentialPattern) { match -> "${match.groupValues[1]}[REDACTED]@" }
            .replace(secretQueryParameterPattern) { match -> "${match.groupValues[1]}[REDACTED]" }
}
