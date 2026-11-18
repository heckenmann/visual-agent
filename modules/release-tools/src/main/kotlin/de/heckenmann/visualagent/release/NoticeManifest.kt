package de.heckenmann.visualagent.release

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.nio.file.Files
import java.nio.file.Path

/** Writes deterministic sorted evidence metadata without changing retained material bytes. */
@OptIn(ExperimentalSerializationApi::class)
internal fun writeNoticeManifest(
    path: Path,
    report: JsonObject,
) {
    val format =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }
    Files.writeString(path, format.encodeToString(JsonElement.serializer(), sortedNoticeJson(report)) + "\n")
}

private fun sortedNoticeJson(element: JsonElement): JsonElement =
    when (element) {
        is JsonObject -> JsonObject(element.toSortedMap().mapValues { sortedNoticeJson(it.value) })
        is JsonArray -> JsonArray(element.map(::sortedNoticeJson))
        else -> element
    }
