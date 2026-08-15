package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.error.ErrorMessageMapper
import de.heckenmann.visualagent.protocol.CancellationToken
import de.heckenmann.visualagent.protocol.ConversationClearResult
import de.heckenmann.visualagent.protocol.ConversationHistoryPage
import de.heckenmann.visualagent.protocol.ConversationInputPlacement
import de.heckenmann.visualagent.protocol.ConversationMessage
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.ConversationPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import de.heckenmann.visualagent.agent.CancellationToken as ApplicationCancellationToken

/** Spring-side adapter that keeps application models behind the conversation protocol port. */
@Component
class SpringConversationPort(
    private val agentManager: AgentManager,
    private val appConfig: AppConfigBean,
) : ConversationPort {
    override suspend fun latest(): ConversationHistoryPage =
        withContext(Dispatchers.IO) { protocolBoundary { agentManager.readLatestHistoryPage().toConversationPage() } }

    override suspend fun older(offset: Int): ConversationHistoryPage =
        withContext(Dispatchers.IO) { protocolBoundary { agentManager.readOlderHistoryPage(offset).toConversationPage() } }

    override suspend fun stream(
        content: String,
        token: CancellationToken,
        onChunk: (String) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            protocolBoundary {
                val applicationToken = ApplicationCancellationToken()
                token.onCancelled(applicationToken::cancel)
                agentManager.streamMessage(content, applicationToken, onChunk)
            }
        }
    }

    override fun currentHistory(): List<ConversationMessage> =
        protocolBoundary {
            agentManager.getHistory().map(Message::toConversationMessage)
        }

    override fun deleteMessage(id: String): Boolean =
        protocolBoundary {
            agentManager.deleteMessageById(id)
            true
        }

    override fun updateMessage(
        id: String,
        content: String,
    ): Boolean =
        protocolBoundary {
            agentManager.updateMessageContentById(id, content)
            true
        }

    override fun cancelActiveWork() {
        protocolBoundary {
            agentManager.cancelAllRunningActions()
            agentManager.cancelAllActiveTodos()
        }
    }

    override suspend fun clearAndCreateWelcome(): ConversationClearResult =
        protocolBoundary {
            agentManager.clearHistory()
            val result = agentManager.addWelcomeMessageAfterReset()
            when (result) {
                is de.heckenmann.visualagent.agent.conversation.WelcomeResult.Generated -> ConversationClearResult()
                is de.heckenmann.visualagent.agent.conversation.WelcomeResult.Fallback ->
                    ConversationClearResult(
                        ErrorMessageMapper.map(result.error).detail,
                    )
            }
        }

    override fun preferences(): ConversationPreferences =
        ConversationPreferences(
            inputPlacement =
                when (appConfig.conversationInputPlacement) {
                    de.heckenmann.visualagent.config.ConversationInputPlacement.FIXED -> ConversationInputPlacement.FIXED
                    de.heckenmann.visualagent.config.ConversationInputPlacement.CONVERSATION_MESSAGE ->
                        ConversationInputPlacement.CONVERSATION_MESSAGE
                },
            queueFlushMode = appConfig.queueFlushMode,
        )

    override fun updatePreferences(preferences: ConversationPreferences) {
        appConfig.conversationInputPlacement =
            when (preferences.inputPlacement) {
                ConversationInputPlacement.FIXED -> de.heckenmann.visualagent.config.ConversationInputPlacement.FIXED
                ConversationInputPlacement.CONVERSATION_MESSAGE ->
                    de.heckenmann.visualagent.config.ConversationInputPlacement.CONVERSATION_MESSAGE
            }
        appConfig.queueFlushMode = preferences.queueFlushMode
        appConfig.save()
    }
}

private fun de.heckenmann.visualagent.agent.conversation.ConversationHistoryPage.toConversationPage(): ConversationHistoryPage =
    ConversationHistoryPage(messages.map(Message::toConversationMessage), offset, hasMore)

private fun Message.toConversationMessage(): ConversationMessage = ConversationMessage(role, content, metadata, images, id)
