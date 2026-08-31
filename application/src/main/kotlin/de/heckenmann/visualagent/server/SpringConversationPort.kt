package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.clearTodos
import de.heckenmann.visualagent.agent.conversation.ResponseTelemetryMetadata
import de.heckenmann.visualagent.config.AppConfigBean
import de.heckenmann.visualagent.error.ErrorMessageMapper
import de.heckenmann.visualagent.protocol.CancellationToken
import de.heckenmann.visualagent.protocol.ConversationClearResult
import de.heckenmann.visualagent.protocol.ConversationHistoryPage
import de.heckenmann.visualagent.protocol.ConversationImageResolution
import de.heckenmann.visualagent.protocol.ConversationInputPlacement
import de.heckenmann.visualagent.protocol.ConversationMessage
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.ConversationPreferences
import de.heckenmann.visualagent.protocol.ConversationResponseTelemetry
import de.heckenmann.visualagent.protocol.ConversationStreamRequest
import de.heckenmann.visualagent.protocol.ConversationStreamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.springframework.stereotype.Component
import java.util.Base64
import de.heckenmann.visualagent.agent.CancellationToken as ApplicationCancellationToken

/** Spring-side adapter that keeps application models behind the conversation protocol port. */
@Component
class SpringConversationPort(
    private val agentManager: AgentManager,
    private val appConfig: AppConfigBean,
    private val mediaResolver: ConversationMediaResolver,
) : ConversationPort {
    override suspend fun latest(): ConversationHistoryPage =
        withContext(Dispatchers.IO) { protocolBoundary { agentManager.readLatestHistoryPage().toConversationPage(mediaResolver) } }

    override suspend fun older(offset: Int): ConversationHistoryPage =
        withContext(Dispatchers.IO) { protocolBoundary { agentManager.readOlderHistoryPage(offset).toConversationPage(mediaResolver) } }

    override suspend fun stream(
        request: ConversationStreamRequest,
        token: CancellationToken,
        onChunk: (String) -> Unit,
    ): ConversationStreamResult =
        withContext(Dispatchers.IO) {
            protocolBoundary {
                val applicationToken = ApplicationCancellationToken()
                token.onCancelled(applicationToken::cancel)
                agentManager.streamMessage(request.content, applicationToken, onChunk, request.userEntryId, request.assistantEntryId)
                val message =
                    agentManager
                        .getHistory()
                        .lastOrNull { it.id == request.assistantEntryId }
                        ?: error("Conversation stream completed without its assistant entry")
                ConversationStreamResult(message.toConversationMessage(mediaResolver))
            }
        }

    override suspend fun resolveImage(source: String): ConversationImageResolution =
        withContext(Dispatchers.IO) { mediaResolver.resolve(source) }

    override suspend fun currentHistory(): List<ConversationMessage> =
        withContext(Dispatchers.IO) {
            protocolBoundary {
                agentManager.loadLatestHistory()
                agentManager.getHistory().map { it.toConversationMessage(mediaResolver) }
            }
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
            agentManager.clearTodos()
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

private fun de.heckenmann.visualagent.agent.conversation.ConversationHistoryPage.toConversationPage(
    mediaResolver: ConversationMediaResolver,
): ConversationHistoryPage =
    ConversationHistoryPage(
        messages.map { it.toConversationMessage(mediaResolver) },
        offset,
        hasMore,
    )

private fun Message.toConversationMessage(mediaResolver: ConversationMediaResolver): ConversationMessage {
    val responseMetadata = ResponseTelemetryMetadata.decode(metadata)
    return ConversationMessage(
        role = role,
        content = content,
        metadata = metadata,
        images =
            (images.orEmpty() + metadataImageDataUrls(metadata))
                .mapNotNull { image -> mediaResolver.resolveEmbedded(image).asDataUrl() }
                .takeIf { it.isNotEmpty() },
        id = id,
        createdAtEpochMillis = createdAtEpochMillis,
        timelineSequence = timelineSequence,
        reasoning = responseMetadata?.reasoning,
        telemetry =
            responseMetadata?.let { telemetry ->
                ConversationResponseTelemetry(
                    model = telemetry.model,
                    finishReason = telemetry.finishReason?.name,
                    totalMillis = telemetry.totalMillis,
                    timeToFirstTokenMillis = telemetry.timeToFirstTokenMillis,
                    promptEvaluationMillis = telemetry.promptEvaluationMillis,
                    generationMillis = telemetry.generationMillis,
                    promptTokens = telemetry.promptTokens,
                    completionTokens = telemetry.completionTokens,
                    totalTokens = telemetry.totalTokens,
                )
            },
    )
}

private fun metadataImageDataUrls(metadata: String?): List<String> {
    val element = metadata?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() } as? JsonObject ?: return emptyList()
    val dataUrl = element["dataUrl"] as? JsonPrimitive ?: return emptyList()
    return dataUrl
        .contentOrNull
        ?.takeIf(String::isNotBlank)
        ?.let(::listOf)
        .orEmpty()
}

private fun ConversationImageResolution.asDataUrl(): String? =
    (this as? ConversationImageResolution.Loaded)?.let { loaded ->
        "data:${loaded.mimeType};base64,${Base64.getEncoder().encodeToString(loaded.bytes)}"
    }
