package io.github.vupoint.cokit.client

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

@Serializable
data class DynamicToolSpec(
    val name: String,
    val description: String,
    val inputSchema: CodexJsonPayload,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val type: String = "function",
)

@Serializable
data class DynamicToolCallRequest(
    val threadId: ThreadId,
    val turnId: TurnId,
    val callId: String,
    val tool: String,
    val arguments: CodexJsonPayload,
    val namespace: String? = null,
)

@Serializable
data class DynamicToolCallResponse(
    val success: Boolean,
    val contentItems: List<DynamicToolCallContentItem>,
)

@Serializable
data class DynamicToolCallContentItem(
    val text: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val type: String = "inputText",
)

fun interface DynamicToolCallHandler {
    suspend fun call(request: DynamicToolCallRequest): DynamicToolCallResponse
}
