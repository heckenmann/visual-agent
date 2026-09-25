package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.AgentManagerConstants
import de.heckenmann.visualagent.agent.Message

/** Loads only the recent database history that can plausibly fit the configured model context. */
internal class MainAgentContextHistoryLoader(
    private val owner: AgentManager,
) {
    /** Reads a bounded context window and converts its persisted rows to provider messages. */
    fun load(
        userTurnLimit: Int,
        recordLimit: Int,
    ): List<Message> {
        val maximumRecords = owner.appConfig.contextLength.coerceAtLeast(1)
        return owner.conversationStore
            .getConversationMessagesForContext(
                AgentManagerConstants.MAIN_SESSION_ID,
                userTurnLimit.coerceIn(1, maximumRecords),
                recordLimit.coerceIn(1, maximumRecords),
            ).mapNotNull { row ->
                row
                    .takeIf {
                        it.role.isNotBlank() && (it.content.isNotBlank() || it.role == "assistant" && it.assistantToolTurn)
                    }?.let {
                        Message(
                            role = it.role,
                            content =
                                if (it.role == "assistant") {
                                    owner.responseCoordinator.normalizeAssistantPresentationContent(it.content)
                                } else {
                                    it.content
                                },
                            metadata = it.metadata?.ifBlank { null },
                            id = it.id,
                            createdAtEpochMillis = it.createdAt.toEpochMilli(),
                            timelineSequence = it.timelineSequence,
                            contextPolicy = it.contextPolicy,
                            parentAssistantTurnId = it.parentAssistantTurnId,
                            turnOrder = it.turnOrder,
                            assistantToolTurn = it.assistantToolTurn,
                            conversationRequestId = it.conversationRequestId,
                        )
                    }
            }
    }
}
