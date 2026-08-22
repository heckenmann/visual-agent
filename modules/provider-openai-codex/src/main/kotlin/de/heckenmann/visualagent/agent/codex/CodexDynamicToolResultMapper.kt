package de.heckenmann.visualagent.agent.codex

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Maps Visual Agent tool results to Codex app-server dynamic-tool content items. */
internal object CodexDynamicToolResultMapper {
    /** Converts a serialized callback result into textual and optional inline media items. */
    fun contentItems(serializedResult: String): JsonArray {
        val outer = parseObject(serializedResult)
        val content = outer?.get("content")?.jsonPrimitive?.contentOrNull
        val payload = parseObject(content ?: serializedResult)
        val media = payload?.media()
        return buildJsonArray {
            if (media == null) {
                add(textItem(content ?: serializedResult))
            } else {
                payload["path"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)?.let { path ->
                    add(textItem("Media result for $path"))
                }
                add(media)
            }
        }
    }

    private fun JsonObject.media(): JsonObject? {
        val mimeType = this["mimeType"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: return null
        val base64 = this["base64"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return null
        val mediaType =
            when {
                mimeType.startsWith("image/") -> "inputImage" to "imageUrl"
                mimeType.startsWith("audio/") -> "inputAudio" to "audioUrl"
                else -> return null
            }
        return buildJsonObject {
            put("type", JsonPrimitive(mediaType.first))
            put(mediaType.second, JsonPrimitive("data:$mimeType;base64,$base64"))
        }
    }

    private fun textItem(text: String): JsonObject =
        buildJsonObject {
            put("type", JsonPrimitive("inputText"))
            put("text", JsonPrimitive(text))
        }

    private fun parseObject(value: String): JsonObject? =
        runCatching {
            Json.parseToJsonElement(value).jsonObject
        }.getOrNull()
}
