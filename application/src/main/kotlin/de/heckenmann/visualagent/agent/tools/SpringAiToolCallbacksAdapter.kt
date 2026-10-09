package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.AssistantTurnIdentity
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ProviderToolCall
import de.heckenmann.visualagent.agent.provider.ProviderToolCallbacks
import de.heckenmann.visualagent.agent.tools.api.ToolId
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.ToolCallback
import reactor.core.publisher.Mono
import de.heckenmann.visualagent.agent.ToolId as ProviderToolId
import org.springframework.ai.tool.definition.ToolDefinition as SpringToolDefinition

/** Spring AI adaptation kept at the application composition boundary. */
class SpringAiToolCallbacksAdapter(
    private val registry: ToolRegistry,
    private val agentManager: (() -> AgentManager)? = null,
    private val batches: ToolBatchExecutor = ToolBatchExecutor(registry),
) : ProviderToolCallbacks {
    override fun functionCallbacks(
        enabledTools: Set<ProviderToolId>,
        context: Map<String, Any>,
    ): List<ToolCallback> {
        val requestToolIds =
            if (ToolId(TOOL_HELP_ID) in registry.allToolIds() && ProviderToolId(TOOL_HELP_ID) in enabledTools) {
                enabledTools + ProviderToolId(TOOL_HELP_ID)
            } else {
                enabledTools
            }
        val requestContext =
            context +
                ("enabledTools" to enabledTools.map { it.value }.toSet()) +
                toolCancellationRegistrar(context)
        val resolved = registry.resolve(requestToolIds.mapTo(mutableSetOf()) { ToolId(it.value) })
        return resolved
            .filter {
                it.definition.id.value != "tools:batch" ||
                    resolved.any { other -> other.definition.id.value !in setOf("tools:batch", "javascript:execute", "tool:help") }
            }.map { tool ->
                /** Provider callback delegating one resolved tool to the provider-neutral registry. */
                object : ToolCallback {
                    override fun getToolDefinition(): SpringToolDefinition =
                        SpringToolDefinition
                            .builder()
                            .name(registry.definition(tool).name)
                            .description(registry.definition(tool).description)
                            .inputSchema(registry.definition(tool).inputSchema)
                            .build()

                    override fun call(functionInput: String): String =
                        registry.executeBlocking(
                            tool,
                            functionInput,
                            requestContext,
                        )

                    override fun call(
                        functionInput: String,
                        toolContext: ToolContext?,
                    ): String =
                        registry.executeBlocking(
                            tool,
                            functionInput,
                            requestContext + (toolContext?.context ?: emptyMap()),
                        )
                }
            }
    }

    override fun toolRuntimeGuidance(): String = registry.runtimeGuidance()

    private fun toolCancellationRegistrar(context: Map<String, Any>): Map<String, Any> {
        val parent = context["cancellationToken"] as? CancellationToken ?: return emptyMap()
        return mapOf("toolCancellationRegistrar" to ToolCancellationRegistrar(parent::onCancelled))
    }

    override fun executeToolCallRound(
        toolCalls: List<ProviderToolCall>,
        round: Int,
        parentAssistantTurnId: String?,
        enabledFunctionNames: Set<String>,
        context: Map<String, Any>,
    ): Mono<List<String>> =
        NativeToolBatchRound(registry, batches).execute(
            toolCalls,
            round,
            parentAssistantTurnId,
            enabledFunctionNames,
            context + toolCancellationRegistrar(context),
        )

    override fun recordAssistantToolTurn(
        turn: de.heckenmann.visualagent.agent.ProviderTurnResponse,
        context: Map<String, Any>,
    ): String? {
        if (context["agent"] != "main") return null
        val requestId = context["requestId"]?.toString()?.takeIf(String::isNotBlank) ?: return null
        val manager = agentManager?.invoke() ?: return null
        val round = turn.metadata.round ?: 0
        val turnId = AssistantTurnIdentity.forRound(requestId, round)
        manager.recordProviderAssistantTurn(turn, turnId, requestId)
        return turnId
    }

    private companion object {
        const val TOOL_HELP_ID = "tool:help"
    }
}
