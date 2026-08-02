package de.heckenmann.visualagent.knowledge

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "long_term_memory")
internal class MemoryEntity(
    @Id
    var id: String = "",
    @Column(nullable = false)
    var content: String = "",
    @Lob
    var embedding: ByteArray? = null,
    var tags: String? = null,
    @Convert(converter = InstantStringConverter::class)
    @Column(name = "created_at", columnDefinition = "TIMESTAMP")
    var createdAt: Instant = Instant.EPOCH,
    @Column(name = "access_count")
    var accessCount: Int = 0,
    @Convert(converter = InstantStringConverter::class)
    @Column(name = "last_accessed", columnDefinition = "TIMESTAMP")
    var lastAccessed: Instant? = null,
)

@Entity
@Table(name = "project_knowledge")
internal class ProjectKnowledgeEntity(
    @Id
    var id: String = "",
    @Column(name = "project_path", nullable = false, unique = true)
    var projectPath: String = "",
    var name: String? = null,
    var description: String? = null,
    var summary: String? = null,
    @Convert(converter = InstantStringConverter::class)
    @Column(name = "last_accessed", columnDefinition = "TIMESTAMP")
    var lastAccessed: Instant? = null,
)

@Entity
@Table(name = "user_preferences")
internal class PreferenceEntity(
    @Id
    @Column(name = "key")
    var key: String = "",
    @Column(nullable = false)
    var value: String = "",
    var type: String = "string",
    @Convert(converter = InstantStringConverter::class)
    @Column(name = "updated_at", columnDefinition = "TIMESTAMP")
    var updatedAt: Instant = Instant.EPOCH,
)

@Entity
@Table(name = "conversation_history")
internal class ConversationEntity(
    @Id
    var id: String = "",
    @Column(name = "session_id", nullable = false)
    var sessionId: String = "",
    @Column(nullable = false)
    var role: String = "",
    @Column(nullable = false)
    var content: String = "",
    var metadata: String? = null,
    @Convert(converter = InstantStringConverter::class)
    @Column(name = "created_at", columnDefinition = "TIMESTAMP")
    var createdAt: Instant = Instant.EPOCH,
)

@Entity
@Table(name = "workspace_files")
internal class WorkspaceFileEntity(
    @Id
    var id: String = "",
    @Column(name = "relative_path", nullable = false, unique = true)
    var relativePath: String = "",
    @Column(name = "original_name", nullable = false)
    var originalName: String = "",
    @Column(name = "mime_type", nullable = false)
    var mimeType: String = "",
    @Column(name = "size_bytes", nullable = false)
    var sizeBytes: Long = 0L,
    @Column(nullable = false)
    var sha256: String = "",
    @Lob
    @Column(name = "extracted_text")
    var extractedText: String? = null,
    @Convert(converter = InstantStringConverter::class)
    @Column(name = "imported_at", columnDefinition = "TIMESTAMP")
    var importedAt: Instant = Instant.EPOCH,
    @Convert(converter = InstantStringConverter::class)
    @Column(name = "updated_at", columnDefinition = "TIMESTAMP")
    var updatedAt: Instant = Instant.EPOCH,
)

@Entity
@Table(name = "todos")
internal class TodoEntity(
    @Id
    var id: String = "",
    @Column(nullable = false)
    var description: String = "",
    var status: String = "PENDING",
    var position: Int = 0,
    @Column(name = "assigned_agent_id")
    var assignedAgentId: String? = null,
    @Convert(converter = InstantStringConverter::class)
    @Column(name = "created_at", columnDefinition = "TIMESTAMP")
    var createdAt: Instant = Instant.EPOCH,
    @Convert(converter = InstantStringConverter::class)
    @Column(name = "completed_at", columnDefinition = "TIMESTAMP")
    var completedAt: Instant? = null,
    @Convert(converter = InstantStringConverter::class)
    @Column(name = "due_date", columnDefinition = "TIMESTAMP")
    var dueDate: Instant? = null,
)

@Entity
@Table(name = "sub_agents")
internal class SubAgentEntity(
    @Id
    var id: String = "",
    @Column(nullable = false)
    var name: String = "",
    @Column(nullable = false)
    var role: String = "",
    var status: String = "IDLE",
    @Column(name = "current_task")
    var currentTask: String? = null,
    @Column(name = "parent_agent_id")
    var parentAgentId: String? = null,
    @Column(nullable = false)
    var config: String = "",
    @Convert(converter = InstantStringConverter::class)
    @Column(name = "created_at", columnDefinition = "TIMESTAMP")
    var createdAt: Instant = Instant.EPOCH,
    @Convert(converter = InstantStringConverter::class)
    @Column(name = "updated_at", columnDefinition = "TIMESTAMP")
    var updatedAt: Instant = Instant.EPOCH,
)

@Entity
@Table(name = "sub_agent_configs")
internal class SubAgentConfigEntity(
    @Id
    var id: String = "",
    @Column(nullable = false)
    var name: String = "",
    @Column(nullable = false)
    var description: String = "",
    @Column(nullable = false)
    var model: String = "",
    @Column(name = "system_prompt", nullable = false)
    var systemPrompt: String = "",
    @Column(nullable = false)
    var tools: String = "[]",
    @Column(name = "max_turns", nullable = false)
    var maxTurns: Int = 5,
    @Convert(converter = BooleanIntegerConverter::class)
    @Column(nullable = false, columnDefinition = "INTEGER")
    var enabled: Boolean = true,
    @Convert(converter = InstantStringConverter::class)
    @Column(name = "created_at", columnDefinition = "TIMESTAMP")
    var createdAt: Instant = Instant.EPOCH,
)
