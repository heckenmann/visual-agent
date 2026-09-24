package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.AgentManagerConstants
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.ContextWindow
import de.heckenmann.visualagent.agent.ConversationContextPolicy
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ToolId
import de.heckenmann.visualagent.agent.context.MainAgentLongTermMemoryPrompt
import de.heckenmann.visualagent.agent.context.MainAgentRuntimeStatePrompt

/** Builds bounded, provider-safe request context for the main agent. */
internal class AgentManagerContextOps(
    private val owner: AgentManager,
) {
    private val contextAssembler = MainAgentContextAssembler()

    internal fun buildMainRequest(
        history: List<Message>,
        requestId: String? = null,
        token: CancellationToken? = null,
    ): ChatRequestContext {
        val toolingAvailable = owner.providerCatalog.activeModelSupportsToolCalling()
        val contextPrompt = buildMainSystemContextPrompt(toolingAvailable)
        val enabledTools =
            owner.agentToolConfigService
                .mainAgentTools()
                .takeIf { toolingAvailable }
                ?.toSet()
                .orEmpty()
        val runtimeStatePrompt =
            MainAgentRuntimeStatePrompt.compose(
                todos = owner.todoStore.listTodos(),
                subAgents = owner.getSubAgents(),
            )
        val memoryPrompt =
            MainAgentLongTermMemoryPrompt.compose(
                owner.mainAgentLongTermMemoryStore.snapshot(),
                owner.appConfig.maxMainAgentMemoryChars,
                ToolId("memory") in enabledTools,
                toolingAvailable,
            )
        val preparedMessages = mutableListOf<Message>()
        preparedMessages += Message("system", contextPrompt)
        preparedMessages += Message("assistant", memoryPrompt, contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE)
        preparedMessages += Message("assistant", runtimeStatePrompt, contextPolicy = ConversationContextPolicy.SUMMARY_SOURCE)
        preparedMessages +=
            contextAssembler
                .assemble(history)
                .map(::normalizeHistoryRoleForProvider)
        val metadata =
            mutableMapOf<String, Any>(
                "sessionId" to AgentManagerConstants.MAIN_SESSION_ID,
                "agent" to "main",
                "thinkingEnabled" to true,
            ).apply {
                if (!requestId.isNullOrBlank()) put("requestId", requestId)
            }
        return ChatRequestContext(
            messages = preparedMessages,
            enabledTools = enabledTools,
            metadata = metadata,
            cancellationToken = token,
            contextWindow = ContextWindow(configuredLimit = owner.appConfig.contextLength),
        )
    }

    private fun normalizeHistoryRoleForProvider(message: Message): Message =
        when (message.role) {
            "tool" -> message.copy(role = "assistant")
            "sub_agent" -> message.copy(role = "assistant")
            "assistant" -> message.copy(content = owner.responseCoordinator.normalizeAssistantContent(message.content))
            else -> message
        }

    internal fun buildMainSystemContextPrompt(toolingAvailable: Boolean = owner.providerCatalog.activeModelSupportsToolCalling()): String =
        de.heckenmann.visualagent.agent.context.MainSystemPromptComposer
            .compose(
                pendingResumeMessage = owner.pendingResumeMessage,
                toolConfigService = owner.agentToolConfigService,
                userModelInstruction = owner.appConfig.userModelInstruction,
                toolingAvailable = toolingAvailable,
            )
}
