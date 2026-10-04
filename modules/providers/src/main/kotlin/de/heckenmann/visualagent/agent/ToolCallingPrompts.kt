package de.heckenmann.visualagent.agent

import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.ToolCallingChatOptions
import org.springframework.ai.model.tool.ToolExecutionResult
import org.springframework.ai.tool.ToolCallback

/** Binds enabled callbacks without discarding provider-specific response options. */
internal fun bindToolCallbacks(
    prompt: Prompt,
    toolCallbacks: List<ToolCallback>,
): Prompt {
    val options = prompt.options
    val boundOptions =
        if (options is ToolCallingChatOptions) {
            options.mutate().toolCallbacks(toolCallbacks).build()
        } else {
            ToolCallingChatOptions.builder().toolCallbacks(toolCallbacks).build()
        }
    return Prompt(prompt.instructions, boundOptions)
}

/** Retains provider response options when appending a completed tool round. */
internal fun appendToolConversationHistory(
    prompt: Prompt,
    toolExecutionResult: ToolExecutionResult,
): Prompt = Prompt(toolExecutionResult.conversationHistory(), prompt.options)
