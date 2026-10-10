package de.heckenmann.visualagent.agent.javascript

import de.heckenmann.visualagent.agent.tools.api.ToolResultEnvelope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.graalvm.polyglot.proxy.ProxyArray
import org.graalvm.polyglot.proxy.ProxyObject

/** Pure result conversion; no context, scheduling or host capability is exposed. */
internal object JavaScriptToolResults {
    /** Converts a canonical registry result to JSON-compatible guest proxies. */
    fun envelope(
        result: ToolResultEnvelope,
        id: String? = null,
    ): ProxyObject =
        ProxyObject.fromMap(
            mapOf(
                "toolId" to result.toolId,
                "success" to result.success,
                "data" to jsonToGuest(result.data),
                "error" to
                    result.error?.let { error ->
                        ProxyObject.fromMap(
                            mapOf(
                                "code" to error.code.name,
                                "message" to error.message,
                                "remediation" to error.remediation,
                                "retryable" to error.retryable,
                            ),
                        )
                    },
            ) + (id?.let { mapOf("id" to it) } ?: emptyMap()),
        )

    private fun jsonToGuest(element: JsonElement): Any? =
        when (element) {
            JsonNull -> null
            is JsonPrimitive -> if (element.isString) element.content else element.booleanOrNumber()
            is JsonArray -> ProxyArray.fromList(element.map(::jsonToGuest))
            is JsonObject -> ProxyObject.fromMap(element.mapValues { (_, value) -> jsonToGuest(value) })
        }

    private fun JsonPrimitive.booleanOrNumber(): Any =
        when {
            content.equals("true", ignoreCase = true) -> true
            content.equals("false", ignoreCase = true) -> false
            else -> content.toDoubleOrNull() ?: content
        }
}
