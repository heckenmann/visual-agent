package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.AgentManagerConstants
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.provider.ProviderErrorMessages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.awaitSingle
import java.util.UUID

/** Resumes interrupted requests with the same reset protection as ordinary turns. */
internal class AgentConversationRecoveryOps(
    private val owner: AgentManager,
    private val buildMainRequest: (List<Message>, String?) -> ChatRequestContext,
    private val loadHistory: () -> List<Message>,
    private val persist: (Message) -> Message,
) {
    /** Registers recovery before provider work so a concurrent reset invalidates its result. */
    fun resumeIfNeeded() {
        if (owner.pendingResumeMessage == null) return
        val requestId = UUID.randomUUID().toString()
        owner.conversationStore.beginConversationRequest(AgentManagerConstants.MAIN_SESSION_ID, requestId)
        owner.scope.launch {
            try {
                if (!owner.llmProvider.checkConnectionReactive().awaitSingle()) {
                    persist(
                        Message(
                            "assistant",
                            "I could not resume the previous request automatically. The configured provider is currently unreachable.",
                            conversationRequestId = requestId,
                        ),
                    )
                    return@launch
                }
                val request = buildMainRequest(loadHistory(), requestId)
                val messages = request.messages.toMutableList()
                val systemContextIndex = messages.indexOfFirst { it.role == "system" }
                messages.add(
                    if (systemContextIndex >= 0) systemContextIndex + 1 else 0,
                    Message(
                        "system",
                        "The previous request was interrupted by an app shutdown or failure. Continue the unfinished work from the last user request now.",
                    ),
                )
                val response = owner.llmProvider.chatReactive(request.copy(messages = messages)).awaitSingle()
                val persisted =
                    persist(
                        Message(
                            "assistant",
                            owner.responseCoordinator.normalizeAssistantPresentationContent(response.message.content),
                            conversationRequestId = requestId,
                        ),
                    )
                owner.conversationOps.publishAssistantCompletion(persisted)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val detail = ProviderErrorMessages.userFacing(error)
                persist(
                    Message(
                        "assistant",
                        "I could not resume the previous request automatically. $detail",
                        conversationRequestId = requestId,
                    ),
                )
            } finally {
                owner.pendingResumeMessage = null
            }
        }
    }
}
