package de.heckenmann.visualagent.agent.codex

import de.heckenmann.visualagent.agent.VisionSupport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.tool.ToolCallback
import java.nio.file.Path

/** Builds validated Codex app-server request parameters for text and image turns. */
internal object CodexAppServerRequestParams {
    /** Builds the thread initialization parameters and dynamic tool definitions. */
    fun thread(
        prompt: Prompt,
        model: String,
        workingDirectory: Path,
        toolCallbacks: List<ToolCallback>,
    ): JsonObject =
        buildJsonObject {
            put("model", JsonPrimitive(model))
            put("cwd", JsonPrimitive(workingDirectory.toAbsolutePath().toString()))
            put("sandbox", JsonPrimitive("read-only"))
            put("approvalPolicy", JsonPrimitive("never"))
            put("ephemeral", JsonPrimitive(true))
            put(
                "config",
                buildJsonObject {
                    put("project_doc_max_bytes", JsonPrimitive(0))
                    put("include_apps_instructions", JsonPrimitive(false))
                    put("include_collaboration_mode_instructions", JsonPrimitive(false))
                },
            )
            prompt.systemInstructions()?.let { put("baseInstructions", JsonPrimitive(it)) }
            put(
                "dynamicTools",
                buildJsonArray {
                    toolCallbacks.forEach { callback ->
                        add(
                            buildJsonObject {
                                put("type", JsonPrimitive("function"))
                                put("name", JsonPrimitive(callback.toolDefinition.name()))
                                put("description", JsonPrimitive(callback.toolDefinition.description()))
                                put("inputSchema", Json.parseToJsonElement(callback.toolDefinition.inputSchema()))
                            },
                        )
                    }
                },
            )
        }

    /** Builds a turn request, optionally appending one inline image user input. */
    fun turn(
        prompt: Prompt,
        threadId: String,
        model: String,
        showReasoningSummary: Boolean,
        image: ByteArray?,
    ): JsonObject =
        buildJsonObject {
            put("threadId", JsonPrimitive(threadId))
            put("model", JsonPrimitive(model))
            put(
                "input",
                buildJsonArray {
                    prompt
                        .conversationMessages()
                        .forEach { message ->
                            add(
                                buildJsonObject {
                                    put("type", JsonPrimitive("text"))
                                    put("text", JsonPrimitive(messageText(message)))
                                },
                            )
                        }
                    image?.let { imageBytes ->
                        add(
                            buildJsonObject {
                                put("type", JsonPrimitive("image"))
                                put("url", JsonPrimitive(VisionSupport.dataUrl(imageBytes)))
                                put("detail", JsonPrimitive("auto"))
                            },
                        )
                    }
                },
            )
            put("summary", JsonPrimitive(if (showReasoningSummary) "detailed" else "none"))
        }

    private fun Prompt.systemInstructions(): String? =
        instructions
            .filterIsInstance<SystemMessage>()
            .joinToString("\n\n") { it.text.orEmpty() }
            .takeIf(String::isNotBlank)

    private fun Prompt.conversationMessages(): List<Message> = instructions.filter { it !is SystemMessage }

    private fun messageText(message: org.springframework.ai.chat.messages.Message): String =
        when (message) {
            is AssistantMessage -> "[assistant]\n${message.text.orEmpty()}"
            is UserMessage -> "[user]\n${message.text.orEmpty()}"
            else -> "[context]\n${message.text.orEmpty()}"
        }
}

/** Extracts the thread identifier from a successful Codex thread response. */
internal fun JsonObject.codexThreadId(): String =
    this["thread"]
        ?.jsonObject
        ?.get("id")
        ?.jsonPrimitive
        ?.contentOrNull
        ?: error("Codex did not return a thread id")
