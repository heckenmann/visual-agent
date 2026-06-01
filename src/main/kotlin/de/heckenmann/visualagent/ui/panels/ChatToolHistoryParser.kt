package de.heckenmann.visualagent.ui.panels

import de.heckenmann.visualagent.agent.Message
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses persisted tool-call metadata into conversation timeline models.
 */
internal class ChatToolHistoryParser {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Returns whether a persisted message contains tool-call metadata.
     */
    fun isToolHistoryEntry(message: Message): Boolean {
        val metadata = message.metadata ?: return false
        return parseToolMetadata(metadata) != null
    }

    /**
     * Parses serialized tool-call metadata.
     */
    fun parseToolMetadata(metadata: String): ToolMessageData? =
        runCatching {
            val root = json.parseToJsonElement(metadata)
            if (root !is JsonObject) return null
            val type = root["type"]?.jsonPrimitive?.content ?: return null
            if (type != "tool_call") return null
            ToolMessageData(
                toolId = root["toolId"]?.jsonPrimitive?.content ?: "tool",
                status = root["status"]?.jsonPrimitive?.content ?: "ok",
                durationMillis = root["durationMillis"]?.jsonPrimitive?.content?.toLongOrNull(),
                inputJson = root["inputJson"]?.jsonPrimitive?.content,
                resultContent = root["resultContent"]?.jsonPrimitive?.content,
                resultError = root["resultError"]?.jsonPrimitive?.content,
            )
        }.getOrNull()
}
