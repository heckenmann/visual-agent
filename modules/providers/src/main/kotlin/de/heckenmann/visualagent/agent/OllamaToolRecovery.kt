package de.heckenmann.visualagent.agent.ollama

import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ChatResponse
import de.heckenmann.visualagent.agent.Message
import org.springframework.ai.chat.model.ChatModel
import org.springframework.stereotype.Component

/**
 * Recovery helper for Ollama models that request unknown or unavailable tool callbacks.
 */
@Component
class OllamaToolRecovery(
    private val chatModel: ChatModel,
    private val promptFactory: OllamaPromptFactory,
) {
    /**
     * Builds a deterministic assistant text listing available tool names after an unknown function call.
     *
     * @param allowedFunctionNames Strictly available function names
     * @return User-facing tool-list text
     */
    fun buildToolListResponse(allowedFunctionNames: List<String>): String {
        val names =
            if (allowedFunctionNames.isEmpty()) {
                "none"
            } else {
                allowedFunctionNames.joinToString(", ")
            }
        return "Requested tool function does not exist. Available function names: $names."
    }

    /**
     * Runs one structured recovery turn after an unknown tool call so the model can self-correct.
     *
     * @param request Original request before the failed tool call
     * @param selectedModel Active model name
     * @param allowedFunctionNames Function names exposed for this request
     * @param error Unknown-tool callback exception
     * @return Recovered assistant response, or null when recovery did not produce usable text
     */
    fun runUnknownToolRecovery(
        request: ChatRequestContext,
        selectedModel: String,
        allowedFunctionNames: List<String>,
        error: Throwable,
    ): ChatResponse? {
        val recoveryRequest =
            request.copy(
                messages = listOf(recoveryInstruction(allowedFunctionNames, error)) + request.messages,
            )
        val recoveryModelResponse =
            runCatching {
                chatModel.call(promptFactory.buildPrompt(recoveryRequest, selectedModel))
            }.getOrNull() ?: return null
        val recoveryText =
            recoveryModelResponse.result
                ?.output
                ?.text
                .orEmpty()
                .trim()
        if (recoveryText.isBlank()) return null
        return ChatResponse(
            model = recoveryModelResponse.metadata.model,
            message = Message(role = "assistant", content = recoveryText),
            done = true,
            promptEvalCount =
                recoveryModelResponse.metadata
                    .usage
                    .promptTokens,
            evalCount =
                recoveryModelResponse.metadata
                    .usage
                    .completionTokens,
        )
    }

    private fun recoveryInstruction(
        allowedFunctionNames: List<String>,
        error: Throwable,
    ): Message =
        Message(
            role = "system",
            content =
                """
                Tool call execution failed.
                Error type: tool-error
                Error details: ${error.message.orEmpty()}
                Tool result: {"type":"error","value":"Unknown tool/function name"}
                Available function names: ${allowedFunctionNames.joinToString(", ")}
                Continue the same task. If a tool is required, call only one of the available function names exactly.
                """.trimIndent(),
        )
}
