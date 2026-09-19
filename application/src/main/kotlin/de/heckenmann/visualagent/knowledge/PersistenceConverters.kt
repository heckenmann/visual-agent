package de.heckenmann.visualagent.knowledge

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Persists instants as ISO-8601 text and reads both text and JDBC timestamp representations.
 */
@Converter
internal class InstantStringConverter : AttributeConverter<Instant?, String?> {
    override fun convertToDatabaseColumn(attribute: Instant?): String? = attribute?.toString()

    override fun convertToEntityAttribute(dbData: String?): Instant? =
        dbData?.let { value ->
            runCatching { Instant.parse(value) }
                .recoverCatching {
                    LocalDateTime
                        .parse(value.replace(' ', 'T'), H2_TIMESTAMP_FORMAT)
                        .toInstant(ZoneOffset.UTC)
                }.getOrNull()
        }
}

/**
 * Persists booleans as integer values accepted by the relational schema.
 */
@Converter
internal class BooleanIntegerConverter : AttributeConverter<Boolean?, Int?> {
    override fun convertToDatabaseColumn(attribute: Boolean?): Int? = attribute?.let { if (it) 1 else 0 }

    override fun convertToEntityAttribute(dbData: Int?): Boolean = dbData != null && dbData != 0
}

private val H2_TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSSSSSSSS]")
