package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Adds the registry-owned runtime fields to one provider-visible tool definition. */
public fun ToolDefinition.withRuntimeParameters(): ToolDefinition = copy(inputSchema = inputSchema.withRuntimeParameters())

/** Adds the registry-owned runtime fields to a JSON object schema. */
public fun String.withRuntimeParameters(): String {
    val schema = runCatching { json.parseToJsonElement(this).jsonObject }.getOrElse { return this }
    val properties = schema["properties"] as? JsonObject ?: JsonObject(emptyMap())
    val runtimeProperties =
        buildJsonObject {
            properties.forEach { (name, value) -> put(name, value) }
            if ("timeoutSeconds" !in properties) {
                put(
                    "timeoutSeconds",
                    buildJsonObject {
                        put("type", "integer")
                        put("minimum", MIN_TOOL_TIMEOUT_SECONDS)
                        put("maximum", MAX_TOOL_TIMEOUT_SECONDS)
                        put("description", "Optional maximum duration for this call in seconds")
                    },
                )
            }
            if ("async" !in properties) {
                put(
                    "async",
                    buildJsonObject {
                        put("type", "boolean")
                        put("description", "Run this call in the background when the tool supports it")
                    },
                )
            }
        }
    return buildJsonObject {
        schema.forEach { (name, value) -> put(name, value) }
        put("type", (schema["type"] ?: JsonPrimitive("object")))
        put("properties", runtimeProperties)
    }.toString()
}
