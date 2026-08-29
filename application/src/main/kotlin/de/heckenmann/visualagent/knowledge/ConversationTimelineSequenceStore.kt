package de.heckenmann.visualagent.knowledge

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component

/**
 * Allocates database-generated ordering keys shared by conversation messages and todo activities.
 *
 * Each inserted row receives a monotonically increasing SQLite identity value. The caller invokes
 * this service from its surrounding store transaction, making the resulting key durable together
 * with the message or todo mutation it orders.
 */
@Component
internal class ConversationTimelineSequenceStore(
    private val repository: ConversationTimelineSequenceRepository,
) {
    /** Allocates the next globally ordered timeline key. */
    fun next(): Long = repository.saveAndFlush(ConversationTimelineSequenceEntity()).value
}

/** Persistence adapter for database-generated conversation timeline keys. */
internal interface ConversationTimelineSequenceRepository : JpaRepository<ConversationTimelineSequenceEntity, Long>
