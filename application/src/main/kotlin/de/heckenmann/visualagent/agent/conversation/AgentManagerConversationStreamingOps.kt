package de.heckenmann.visualagent.agent.conversation

import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.AssistantTurnIdentity
import de.heckenmann.visualagent.agent.CancellationToken
import de.heckenmann.visualagent.agent.ChatRequestContext
import de.heckenmann.visualagent.agent.Message
import de.heckenmann.visualagent.agent.ProviderTurnResponse
import de.heckenmann.visualagent.agent.text.ResponseRepetitionGuard
import de.heckenmann.visualagent.protocol.ConversationStreamRequest
import de.heckenmann.visualagent.protocol.ConversationStreamUpdate
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mu.KotlinLogging

/** Owns streamed assistant-turn persistence and transport retry replay. */
internal class AgentManagerConversationStreamingOps(
    private val owner: AgentManager,
    private val loadHistoryContext: () -> List<Message>,
    private val buildRequest: (List<Message>, String?) -> ChatRequestContext,
    private val persist: (Message) -> Message,
    private val publishCompletion: (Message) -> Unit,
    private val mapProviderFailure: (Throwable) -> String,
) {
    private val logger = KotlinLogging.logger {}

    /** Streams provider turns separately and durably records each completed turn. */
    suspend fun streamMessage(
        content: String,
        token: CancellationToken?,
        onChunk: (ConversationStreamUpdate) -> Unit,
        userEntryId: String,
        assistantEntryId: String,
    ): String {
        ConversationStreamRequest(userEntryId, assistantEntryId, content)
        owner.conversationStore.getConversationMessage(assistantEntryId)?.let { existing ->
            require(existing.role == "assistant") { "Conversation retry assistant entry must have role assistant" }
            val userEntry =
                requireNotNull(owner.conversationStore.getConversationMessage(userEntryId)) {
                    "Conversation retry user entry does not exist"
                }
            require(userEntry.role == "user") { "Conversation retry user entry must have role user" }
            require(userEntry.content == content) { "Conversation retry user content does not match" }
            require(userEntry.metadata == conversationTurnMetadata(assistantEntryId)) {
                "Conversation retry entries do not belong to the same turn"
            }
            val turns = owner.conversationStore.getConversationMessagesForRequest(assistantEntryId).ifEmpty { listOf(existing) }
            turns.forEach { turn -> onChunk(ConversationStreamUpdate(turn.id, turn.content)) }
            return turns.last().content
        }
        persist(Message("user", content, metadata = conversationTurnMetadata(assistantEntryId), id = userEntryId))
        val requestId = assistantEntryId
        val collectedByRound = linkedMapOf<Int, StringBuilder>()
        val turnsByRound = linkedMapOf<Int, ProviderTurnResponse>()
        var cancelled = false
        var providerFailure: Throwable? = null
        token?.throwIfCancelled()
        try {
            owner.llmProvider
                .streamReactive(buildRequest(loadHistoryContext(), requestId).copy(cancellationToken = token))
                .doOnNext { chunk ->
                    token?.throwIfCancelled()
                    val round = chunk.providerTurn?.metadata?.round ?: 0
                    chunk.providerTurn?.let { turn -> turnsByRound[round] = ProviderTurnAccumulator.merge(turnsByRound[round], turn) }
                    val part = chunk.message.content
                    if (part.isNotEmpty()) {
                        val collected = collectedByRound.getOrPut(round) { StringBuilder() }
                        val delta = appendStreamPart(collected, part)
                        if (delta.isNotEmpty()) onChunk(ConversationStreamUpdate(AssistantTurnIdentity.forRound(requestId, round), delta))
                    }
                }.then()
                .awaitSingleOrNull()
        } catch (_: kotlinx.coroutines.CancellationException) {
            cancelled = true
            logger.info { "Main agent request $requestId cancelled by user" }
        } catch (error: Throwable) {
            providerFailure = error
        }
        if (providerFailure != null) {
            val failureMessage = mapProviderFailure(requireNotNull(providerFailure))
            persistStreamTurns(requestId, collectedByRound, turnsByRound)
            val failureRound =
                if (collectedByRound.isEmpty() && turnsByRound.isEmpty()) {
                    0
                } else {
                    maxOf(collectedByRound.keys.maxOrNull() ?: 0, turnsByRound.keys.maxOrNull() ?: 0) + 1
                }
            val failureTurnId = AssistantTurnIdentity.forRound(requestId, failureRound)
            onChunk(ConversationStreamUpdate(failureTurnId, failureMessage))
            persist(Message("assistant", failureMessage, id = failureTurnId, conversationRequestId = requestId))
            owner.finishedToolEventsByRequestId.remove(requestId)
            return failureMessage
        }
        val finalRound = maxOf(collectedByRound.keys.maxOrNull() ?: 0, turnsByRound.keys.maxOrNull() ?: 0)
        var assistantText = collectedByRound[finalRound]?.toString().orEmpty()
        val repetitionRetried = ResponseRepetitionGuard.isRunawayRepetition(assistantText.trim())
        if (repetitionRetried) {
            logger.warn { "Repetition guard detected runaway streaming output; retrying once" }
            assistantText = owner.responseCoordinator.retryAfterRepetition()
        }
        val hasToolTurns = turnsByRound.values.any { it.toolCalls.isNotEmpty() }
        var finalPersistedTurn: Message? = null
        if (!cancelled && assistantText.isBlank() && hasToolTurns) {
            val followup = owner.responseCoordinator.completeToolOnlyTurnWithFollowup(requestId)
            if (!followup.isNullOrBlank()) {
                assistantText = followup
                val followupId = AssistantTurnIdentity.forRound(requestId, finalRound + 1)
                onChunk(ConversationStreamUpdate(followupId, followup))
                finalPersistedTurn =
                    persist(
                        Message(
                            "assistant",
                            owner.responseCoordinator.normalizeAssistantPresentationContent(followup),
                            id = followupId,
                            conversationRequestId = requestId,
                        ),
                    )
            }
        }
        persistStreamTurns(
            requestId,
            collectedByRound,
            turnsByRound,
            telemetryOmittedRounds = if (repetitionRetried) setOf(finalRound) else emptySet(),
        )
        if (cancelled) {
            val cancelledRound =
                if (collectedByRound.isEmpty() && turnsByRound.isEmpty()) {
                    0
                } else {
                    finalRound + 1
                }
            val cancelledId = AssistantTurnIdentity.forRound(requestId, cancelledRound)
            onChunk(ConversationStreamUpdate(cancelledId, " (cancelled)"))
            finalPersistedTurn = persist(Message("assistant", "(cancelled)", id = cancelledId, conversationRequestId = requestId))
        } else {
            val completed = finalPersistedTurn ?: owner.conversationHistory.lastOrNull { it.role == "assistant" }
            if (completed != null) publishCompletion(completed)
        }
        owner.finishedToolEventsByRequestId.remove(requestId)
        return owner.responseCoordinator.normalizeAssistantContent(assistantText)
    }

    private fun conversationTurnMetadata(assistantEntryId: String): String =
        buildJsonObject {
            put("type", "conversation_turn")
            put("assistantEntryId", assistantEntryId)
        }.toString()

    private fun persistStreamTurns(
        requestId: String,
        contentByRound: Map<Int, StringBuilder>,
        turnsByRound: Map<Int, ProviderTurnResponse>,
        telemetryOmittedRounds: Set<Int> = emptySet(),
    ) {
        (contentByRound.keys + turnsByRound.keys).distinct().sorted().forEach { round ->
            val id = AssistantTurnIdentity.forRound(requestId, round)
            if (owner.conversationStore.getConversationMessage(id) != null) return@forEach
            val content = contentByRound[round]?.toString().orEmpty()
            val turn = turnsByRound[round]
            val toolTurn = turn?.toolCalls?.isNotEmpty() == true
            val message =
                Message(
                    role = "assistant",
                    content =
                        if (content.isBlank() &&
                            !toolTurn
                        ) {
                            owner.responseCoordinator.normalizeAssistantPresentationContent(content)
                        } else {
                            content
                        },
                    metadata = turn?.takeUnless { round in telemetryOmittedRounds }?.let { ResponseTelemetryMetadata.encode(it, true) },
                    id = id,
                    assistantToolTurn = toolTurn,
                    conversationRequestId = requestId,
                )
            persist(message)
        }
    }
}
