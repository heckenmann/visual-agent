package de.heckenmann.visualagent.knowledge

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Entity
@Table(name = "main_agent_long_term_memory")
internal class MainAgentLongTermMemoryEntity(
    @Id
    var scope: String = MAIN_SCOPE,
    @Column(nullable = false)
    var content: String = "",
    @Column(name = "content_length", nullable = false)
    var contentLength: Int = 0,
    @Column(nullable = false)
    var revision: Long = 0,
    @Convert(converter = InstantStringConverter::class)
    @Column(name = "updated_at", columnDefinition = "TIMESTAMP")
    var updatedAt: Instant = Instant.EPOCH,
)

internal interface MainAgentLongTermMemoryRepository : JpaRepository<MainAgentLongTermMemoryEntity, String> {
    @Modifying(clearAutomatically = true)
    @Query(
        """
        UPDATE MainAgentLongTermMemoryEntity memory
        SET memory.content = :content, memory.contentLength = :contentLength,
            memory.revision = memory.revision + 1, memory.updatedAt = :updatedAt
        WHERE memory.scope = :scope AND memory.revision = :expectedRevision
        """,
    )
    fun replaceIfRevisionMatches(
        @Param("scope") scope: String,
        @Param("content") content: String,
        @Param("contentLength") contentLength: Int,
        @Param("expectedRevision") expectedRevision: Long,
        @Param("updatedAt") updatedAt: Instant,
    ): Int
}

/** Spring Data implementation of the main agent's durable memory document. */
@Service
internal class JpaMainAgentLongTermMemoryStore(
    private val repository: MainAgentLongTermMemoryRepository,
) : MainAgentLongTermMemoryStore {
    @Transactional(readOnly = true)
    override fun snapshot(): MainAgentLongTermMemory = repository.findById(MAIN_SCOPE).orElseThrow().toSnapshot()

    @Transactional
    override fun replace(
        content: String,
        expectedRevision: Long,
        maxCodePoints: Int,
    ): MainAgentLongTermMemoryEdit {
        require(maxCodePoints > 0) { "Main-agent memory limit must be positive" }
        val size = content.codePointCount(0, content.length)
        require(size <= maxCodePoints) { "Main-agent memory has $size characters; maximum is $maxCodePoints" }
        val updated =
            repository.replaceIfRevisionMatches(MAIN_SCOPE, content, size, expectedRevision, Instant.now())
        val snapshot = repository.findById(MAIN_SCOPE).orElseThrow().toSnapshot()
        return if (updated == 1) MainAgentLongTermMemoryEdit.Saved(snapshot) else MainAgentLongTermMemoryEdit.Conflict(snapshot)
    }
}

private fun MainAgentLongTermMemoryEntity.toSnapshot(): MainAgentLongTermMemory =
    MainAgentLongTermMemory(content, contentLength, revision, updatedAt)

private const val MAIN_SCOPE = "main"
