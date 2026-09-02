package de.heckenmann.visualagent.knowledge

import de.heckenmann.visualagent.agent.ConversationContextPolicy
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** JPA-backed conversation persistence with stable caller-provided identities. */
@Service
internal class JpaConversationStore(
    private val repository: ConversationRepository,
    private val timelineSequenceStore: ConversationTimelineSequenceStore,
) : ConversationStore {
    @Transactional
    override fun saveConversationMessage(
        id: String,
        sessionId: String,
        role: String,
        content: String,
        metadata: String?,
    ): String = saveConversationMessage(id, sessionId, role, content, metadata, ConversationContextPolicy.forRole(role))

    @Transactional
    override fun saveConversationMessage(
        id: String,
        sessionId: String,
        role: String,
        content: String,
        metadata: String?,
        contextPolicy: ConversationContextPolicy,
    ): String {
        require(UUID.fromString(id).toString() == id) { "Conversation message ID must be a canonical UUID" }
        val existing = repository.findByIdOrNull(id)
        if (existing != null) {
            require(
                existing.sessionId == sessionId &&
                    existing.role == role &&
                    existing.content == content &&
                    existing.metadata == metadata &&
                    existing.contextPolicy == contextPolicy.name,
            ) { "Conversation message $id conflicts with an existing entry" }
            return existing.id
        }
        repository.save(
            ConversationEntity(
                id = id,
                sessionId = sessionId,
                role = role,
                content = content,
                metadata = metadata,
                contextPolicy = contextPolicy.name,
                createdAt = Instant.now(),
                timelineSequence = timelineSequenceStore.next(),
            ),
        )
        return id
    }

    @Transactional(readOnly = true)
    override fun getConversationMessage(id: String): ConversationRecord? = repository.findByIdOrNull(id)?.toRecord()

    @Transactional(readOnly = true)
    override fun getConversationMessages(
        sessionId: String,
        limit: Int,
    ): List<ConversationRecord> =
        repository
            .findBySessionIdOrderByTimelineSequenceDescCreatedAtDescIdDesc(sessionId, PageRequest.of(0, limit.coerceAtLeast(1)))
            .asReversed()
            .map(ConversationEntity::toRecord)

    @Transactional(readOnly = true)
    override fun getConversationMessagesForContext(
        sessionId: String,
        userTurnLimit: Int,
        recordLimit: Int,
    ): List<ConversationRecord> = repository.findForMainContext(sessionId, userTurnLimit, recordLimit).map(ConversationEntity::toRecord)

    @Transactional(readOnly = true)
    override fun getConversationMessagesPage(
        sessionId: String,
        limit: Int,
        offset: Int,
    ): List<ConversationRecord> =
        repository.findPage(sessionId, limit.coerceAtLeast(1), offset.coerceAtLeast(0)).asReversed().map(ConversationEntity::toRecord)

    override fun searchConversationMessages(
        sessionId: String,
        query: String,
        limit: Int,
    ): List<ConversationRecord> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return emptyList()
        val safeLimit = limit.coerceIn(1, 200)
        return runCatching {
            if (isSafeFtsQuery(normalized)) {
                repository.searchFts(sessionId, normalized, safeLimit)
            } else {
                repository.searchLike(sessionId, normalized, safeLimit)
            }
        }.getOrElse { repository.searchLike(sessionId, normalized, safeLimit) }.map(ConversationEntity::toRecord)
    }

    @Transactional
    override fun deleteConversationMessages(sessionId: String): Int = repository.deleteBySessionId(sessionId)

    @Transactional
    override fun deleteConversationMessageById(id: String): Int {
        repository.deleteById(id)
        return 1
    }

    @Transactional
    override fun updateConversationMessageContent(
        id: String,
        newContent: String,
    ): Int {
        val entity = repository.findByIdOrNull(id) ?: return 0
        entity.content = newContent
        repository.save(entity)
        return 1
    }
}

private fun ConversationEntity.toRecord(): ConversationRecord =
    ConversationRecord(
        id = id,
        role = role,
        content = content,
        metadata = metadata,
        createdAt = createdAt,
        timelineSequence = timelineSequence,
        contextPolicy =
            runCatching { ConversationContextPolicy.valueOf(contextPolicy) }
                .getOrDefault(ConversationContextPolicy.SUMMARY_SOURCE),
    )

private fun isSafeFtsQuery(query: String): Boolean =
    query.isNotBlank() &&
        query.length <= 128 &&
        query.all { it.isLetterOrDigit() || it.isWhitespace() || it == '_' }
