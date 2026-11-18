package de.heckenmann.visualagent.release

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tools.jackson.core.ObjectReadContext
import tools.jackson.core.StreamReadFeature
import tools.jackson.core.json.JsonFactory
import java.nio.file.Files
import java.nio.file.Path

/** Library-backed metadata parsing that rejects duplicate keys before constructing JSON trees. */
internal object EvidenceJson {
    fun read(path: Path): JsonObject {
        val text = Files.readString(path)
        val factory = JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()
        factory.createParser(ObjectReadContext.empty(), text).use { parser ->
            while (parser.nextToken() != null) {
                // Let Jackson enforce duplicate-key and structural constraints at every nesting level.
            }
        }
        return Json.parseToJsonElement(text).jsonObject
    }
}

/** Requires a string-valued metadata field rather than coercing JSON scalars. */
internal fun JsonObject.text(name: String): String {
    val value = getValue(name).jsonPrimitive
    require(value.isString) { "Expected string metadata field: $name" }
    return value.content
}

/** Rejects absolute, platform-dependent, and parent-traversing metadata paths. */
internal fun safeEvidencePath(relative: String): Boolean =
    relative.isNotEmpty() &&
        !relative.startsWith('/') &&
        '\\' !in relative &&
        ':' !in relative &&
        relative.split('/').none { it == ".." }
