package de.heckenmann.visualagent.agent

import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import org.springframework.ai.chat.messages.ToolResponseMessage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.DefaultToolExecutionResult
import org.springframework.ai.model.tool.ToolCallingChatOptions
import org.springframework.ai.model.tool.ToolCallingManager
import org.springframework.ai.model.tool.ToolExecutionResult
import org.springframework.ai.tool.ToolCallback

/** Synchronous Spring AI boundary, invoked only by the bounded-elastic ChatModel state machine. */
internal fun executeCorrelatedTools(
    fallback: ToolCallingManager,
    prompt: Prompt,
    response: ChatResponse,
    correlation: ProviderToolCallbacks?,
    turn: ProviderTurnResponse,
    round: Int,
    parent: String?,
    callbacks: List<ToolCallback>,
    context: Map<String, Any>,
): ToolExecutionResult {
    val toolContext = (prompt.options as? ToolCallingChatOptions)?.toolContext.orEmpty()
    if (correlation != null) validateNativeToolCallLimits(prompt, turn.toolCalls)
    val execution =
        correlation?.executeToolCallRound(
            turn.toolCalls,
            round,
            parent,
            callbacks.map { it.toolDefinition.name() }.toSet(),
            context + toolContext,
        )
            ?: return fallback.executeToolCalls(prompt, response)
    // The existing Spring ChatModel compatibility loop is blocking; the scheduler itself stays native Reactor.
    val results = execution.block() ?: error("Tool round produced no result")
    val responses =
        turn.toolCalls.mapIndexed {
            index,
            call,
            ->
            ToolResponseMessage.ToolResponse(call.id, call.functionName, results[index])
        }
    val toolMessage = ToolResponseMessage.builder().responses(responses).build()
    val history = prompt.instructions + requireNotNull(response.result).output + toolMessage
    return DefaultToolExecutionResult(
        history,
        callbacks
            .filter {
                it.toolDefinition.name() in
                    turn.toolCalls.map(ProviderToolCall::functionName)
            }.all { it.toolMetadata.returnDirect() },
    )
}
