package de.heckenmann.visualagent.agent.codex

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** JSON-RPC message received from the Codex app server. */
internal sealed interface CodexRpcMessage {
    /** JSON-RPC response correlated with a client request. */
    data class Response(
        val id: JsonElement,
        val result: JsonObject?,
        val error: JsonObject?,
    ) : CodexRpcMessage

    /** JSON-RPC request initiated by the Codex app server. */
    data class Request(
        val id: JsonElement,
        val method: String,
        val params: JsonObject,
    ) : CodexRpcMessage

    /** JSON-RPC notification initiated by the Codex app server. */
    data class Notification(
        val method: String,
        val params: JsonObject,
    ) : CodexRpcMessage
}

internal fun jsonRpcRequest(
    id: JsonPrimitive,
    method: String,
    params: JsonObject,
): JsonObject =
    kotlinx.serialization.json.buildJsonObject {
        put("jsonrpc", JsonPrimitive("2.0"))
        put("id", id)
        put("method", JsonPrimitive(method))
        put("params", params)
    }

internal fun jsonRpcNotification(
    method: String,
    params: JsonObject,
): JsonObject =
    kotlinx.serialization.json.buildJsonObject {
        put("jsonrpc", JsonPrimitive("2.0"))
        put("method", JsonPrimitive(method))
        put("params", params)
    }

internal fun jsonRpcSuccess(
    id: JsonElement,
    result: JsonObject,
): JsonObject =
    kotlinx.serialization.json.buildJsonObject {
        put("jsonrpc", JsonPrimitive("2.0"))
        put("id", id)
        put("result", result)
    }

internal fun jsonRpcFailure(
    id: JsonElement,
    code: Int,
    message: String,
): JsonObject =
    kotlinx.serialization.json.buildJsonObject {
        put("jsonrpc", JsonPrimitive("2.0"))
        put("id", id)
        put(
            "error",
            kotlinx.serialization.json.buildJsonObject {
                put("code", JsonPrimitive(code))
                put("message", JsonPrimitive(message))
            },
        )
    }
