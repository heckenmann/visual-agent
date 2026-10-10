package de.heckenmann.visualagent.agent

import org.springframework.ai.chat.messages.ToolResponseMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.DefaultToolCallingManager
import org.springframework.ai.model.tool.DefaultToolExecutionResult
import org.springframework.ai.model.tool.ToolCallLimitExceededException

/** Preserves the default manager's current-user-turn limits before any native batch side effect. */
internal fun validateNativeToolCallLimits(
    prompt: Prompt,
    calls: List<ProviderToolCall>,
) {
    val start = prompt.instructions.indexOfLast { it is UserMessage }.coerceAtLeast(0)
    val counts =
        prompt.instructions
            .drop(start)
            .filterIsInstance<ToolResponseMessage>()
            .flatMap { it.responses }
            .groupingBy { it.name() }
            .eachCount()
            .toMutableMap()
    var total = counts.values.sum()
    calls.forEach { call ->
        val count = (counts[call.functionName] ?: 0) + 1
        counts[call.functionName] = count
        total++
        val partial = DefaultToolExecutionResult(prompt.instructions, false)
        if (count > DefaultToolCallingManager.DEFAULT_MAX_CALLS_PER_TOOL) {
            throw ToolCallLimitExceededException(call.functionName, DefaultToolCallingManager.DEFAULT_MAX_CALLS_PER_TOOL, partial)
        }
        if (total > DefaultToolCallingManager.DEFAULT_MAX_TOTAL_TOOL_CALLS) {
            throw ToolCallLimitExceededException(null, DefaultToolCallingManager.DEFAULT_MAX_TOTAL_TOOL_CALLS, partial)
        }
    }
}
