package de.heckenmann.visualagent.agent.codex

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Maps Visual Agent tool results to Codex app-server dynamic-tool content items. */
internal object CodexDynamicToolResultMapper {
    /**
     * Converts a serialized callback result into a Codex dynamic-tool response.
     *
     * @param serializedResult Serialized [ToolResult] or callback failure text
     * @param allowInlineImage Whether the trusted tool action may return inline image bytes
     * @param allowInlineAudio Reserved for a future negotiated audio-capable Codex protocol
     * @param successOverride Optional transport-level success override for callback failures
     */
    fun response(
        serializedResult: String,
        allowInlineImage: Boolean,
        allowInlineAudio: Boolean = false,
        successOverride: Boolean? = null,
    ): JsonObject {
        val result = parseObject(serializedResult)
        val success = successOverride ?: result?.get("success")?.jsonPrimitive?.booleanOrNull ?: true
        return buildJsonObject {
            put("success", JsonPrimitive(success))
            put("contentItems", contentItems(serializedResult, allowInlineImage, allowInlineAudio))
        }
    }

    /** Converts a serialized callback result into textual and optional inline media items. */
    fun contentItems(
        serializedResult: String,
        allowInlineImage: Boolean = false,
        allowInlineAudio: Boolean = false,
    ): JsonArray {
        val outer = parseObject(serializedResult)
        val content = outer?.get("content")?.jsonPrimitive?.contentOrNull
        val error = outer?.get("error")?.jsonPrimitive?.contentOrNull
        val payload = parseObject(content.orEmpty())
        val media = payload?.media(allowInlineImage, allowInlineAudio)
        val fallbackText =
            listOf(content, error)
                .filterNot { it.isNullOrBlank() }
                .joinToString("\n")
                .ifBlank { serializedResult }
        return buildJsonArray {
            if (media == null) {
                add(textItem(fallbackText))
            } else {
                payload["path"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)?.let { path ->
                    add(textItem("Media result for $path"))
                }
                add(media)
            }
        }
    }

    private fun JsonObject.media(
        allowInlineImage: Boolean,
        allowInlineAudio: Boolean,
    ): JsonObject? {
        val mimeType = this["mimeType"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: return null
        val base64 = this["base64"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return null
        val mediaType =
            when {
                allowInlineImage && mimeType.startsWith("image/") -> "inputImage" to "imageUrl"
                // Reserved for a future Codex protocol/model capability. Callers must keep this
                // disabled until the negotiated app-server schema and model support audio.
                allowInlineAudio && mimeType.startsWith("audio/") -> "inputAudio" to "audioUrl"
                mimeType.startsWith("audio/") -> return null
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
