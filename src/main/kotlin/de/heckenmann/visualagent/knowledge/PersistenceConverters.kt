package de.heckenmann.visualagent.knowledge

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.time.Instant

/**
 * Persists instants as the ISO-8601 text format already used by existing SQLite rows.
 */
@Converter
internal class InstantStringConverter : AttributeConverter<Instant?, String?> {
    override fun convertToDatabaseColumn(attribute: Instant?): String? = attribute?.toString()

    override fun convertToEntityAttribute(dbData: String?): Instant? =
        dbData?.let { value -> runCatching { Instant.parse(value) }.getOrNull() }
}

/**
 * Persists booleans as SQLite-compatible integer values.
 */
@Converter
internal class BooleanIntegerConverter : AttributeConverter<Boolean?, Int?> {
    override fun convertToDatabaseColumn(attribute: Boolean?): Int? = attribute?.let { if (it) 1 else 0 }

    override fun convertToEntityAttribute(dbData: Int?): Boolean = dbData != null && dbData != 0
}
