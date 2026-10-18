package de.heckenmann.visualagent.agent.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Reads an authoritative action enumeration from a tool schema; absent metadata stays unknown. */
internal fun schemaToolActions(schema: String): Set<String>? {
    val root = Json.parseToJsonElement(schema).jsonObject
    val properties = root["properties"]?.jsonObject ?: return null
    val action = properties["action"]?.jsonObject ?: return null
    val values = action["enum"]?.jsonArray ?: return null
    return values.map { it.jsonPrimitive.content }.toSet()
}
