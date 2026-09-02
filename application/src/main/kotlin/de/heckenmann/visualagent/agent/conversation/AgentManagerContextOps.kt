package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.AgentManagerConstants
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.Message

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
        val contextPrompt = buildMainSystemContextPrompt()
        val preparedMessages = mutableListOf<Message>()
        preparedMessages += Message("system", contextPrompt)
        preparedMessages +=
            contextAssembler
                .assemble(history, contextPrompt, owner.appConfig.contextLength)
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
            enabledTools = owner.agentToolConfigService.mainAgentTools(),
            metadata = metadata,
            cancellationToken = token,
        )
    }

    private fun normalizeHistoryRoleForProvider(message: Message): Message =
        when (message.role) {
            "tool" -> message.copy(role = "assistant")
            "sub_agent" -> message.copy(role = "system")
            "assistant" -> message.copy(content = owner.responseCoordinator.removeThinkingMarkup(message.content).trim())
            else -> message
        }

    internal fun buildMainSystemContextPrompt(): String {
        val todos = owner.todoStore.listTodos()
        return de.heckenmann.visualagent.agent.context.MainSystemPromptComposer
            .compose(todos, owner.pendingResumeMessage, owner.agentToolConfigService, owner.appConfig.userModelInstruction)
    }
}
