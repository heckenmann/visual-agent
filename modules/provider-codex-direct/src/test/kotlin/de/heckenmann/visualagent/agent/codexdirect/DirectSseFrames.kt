package de.heckenmann.visualagent.agent.codexdirect

import kotlinx.serialization.json.JsonPrimitive

/** Minimal documented SSE frames, with all text encoded by the JSON library. */
internal object DirectSseFrames {
    fun created(): String = event("response.created", """"response":${response("in_progress", "")}""")

    fun delta(text: String): String =
        event(
            "response.output_text.delta",
            """"delta":${JsonPrimitive(text)},"output_index":0,"content_index":0,"item_id":"msg_1"""",
        )

    fun completed(
        text: String,
        cachedTokens: Long = 0,
    ): String = event("response.completed", """"response":${response("completed", text, cachedTokens = cachedTokens)}""")

    fun failed(code: String): String = event("response.failed", """"response":${response("failed", "", code)}""")

    fun toolCall(): String {
        val output =
            """[
            {"type":"reasoning","id":"rs_1","status":"completed","encrypted_content":"opaque-reasoning","summary":[]},
            {"type":"function_call","id":"fc_1","call_id":"call_1","name":"workspace_file","arguments":"{}","status":"completed"}
        ]""".replace("\n", "")
        return event("response.completed", """"response":${response("completed", "", output = output)}""")
    }

    private fun event(
        type: String,
        fields: String,
    ): String = "event: $type\ndata: {\"type\":\"$type\",\"sequence_number\":1,$fields}\n\n"

    private fun response(
        status: String,
        text: String,
        code: String? = null,
        cachedTokens: Long = 0,
        output: String = """[{"type":"message","id":"msg_1","role":"assistant","status":"completed","content":[{"type":"output_text","text":${JsonPrimitive(
            text,
        )},"annotations":[]}]}]""",
    ): String {
        val error = code?.let { """, "error":{"code":${JsonPrimitive(it)},"message":"synthetic private detail"}""" }.orEmpty()
        return """{
            "id":"resp_1","object":"response","created_at":1,"model":"selected","status":"$status",
            "output":$output,
            "usage":{"input_tokens":10,"output_tokens":2,"total_tokens":12,
            "input_tokens_details":{"cached_tokens":$cachedTokens},"output_tokens_details":{"reasoning_tokens":0}}$error
        }""".replace("\n", "")
    }
}
