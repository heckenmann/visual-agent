package de.heckenmann.visualagent.knowledge

import io.r2dbc.spi.Row
import org.springframework.r2dbc.core.DatabaseClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Shared row conversion and compatibility helpers for the R2DBC store adapters. */
internal object R2dbcPersistenceSupport {
    /** Reads a nullable database value as text without exposing driver-specific types. */
    fun text(
        row: Row,
        column: String,
    ): String? = row.get(column)?.toString()

    /** Reads a required database value as text. */
    fun requiredText(
        row: Row,
        column: String,
    ): String = text(row, column) ?: error("Missing database column '$column'")

    /** Reads a nullable integer value from the H2 driver. */
    fun integer(
        row: Row,
        column: String,
    ): Int? =
        when (val value = row.get(column)) {
            null -> null
            is Number -> value.toInt()
            else -> value.toString().toInt()
        }

    /** Reads a nullable long value from the H2 driver. */
    fun long(
        row: Row,
        column: String,
    ): Long? =
        when (val value = row.get(column)) {
            null -> null
            is Number -> value.toLong()
            else -> value.toString().toLong()
        }

    /** Reads an ISO instant, accepting H2's local timestamp representation as a fallback. */
    fun instant(
        row: Row,
        column: String,
    ): Instant? = text(row, column)?.let(::parseInstant)

    /** Parses persisted timestamp text from current and legacy H2 representations. */
    fun parseInstant(value: String): Instant =
        runCatching { Instant.parse(value) }
            .recoverCatching {
                LocalDateTime
                    .parse(value.replace(' ', 'T'), H2_TIMESTAMP_FORMAT)
                    .toInstant(ZoneOffset.UTC)
            }.getOrElse { error("Invalid persisted timestamp '$value'") }

    /** Binds a nullable string value to a SQL statement. */
    fun bindText(
        statement: DatabaseClient.GenericExecuteSpec,
        name: String,
        value: String?,
    ): DatabaseClient.GenericExecuteSpec = if (value == null) statement.bindNull(name, String::class.java) else statement.bind(name, value)

    /** Converts a blocking compatibility call into a required result. */
    fun <T : Any> Mono<T>.blockRequired(): T = block() ?: error("Reactive persistence returned no result")

    /** Converts a blocking compatibility query into a nullable result. */
    fun <T : Any> Mono<T>.blockNullable(): T? = block()

    /** Completes a blocking compatibility command that has no result value. */
    fun Mono<Void>.blockCompletion() {
        block()
    }

    /** Converts a blocking compatibility query into a list without leaking nulls. */
    fun <T : Any> Flux<T>.blockList(): List<T> = collectList().block().orEmpty()

    private val H2_TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSSSSSSSS]")
}
