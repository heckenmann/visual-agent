package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.knowledge.SkillCreateResult
import de.heckenmann.visualagent.knowledge.SkillDeleteResult
import de.heckenmann.visualagent.knowledge.SkillRecord
import de.heckenmann.visualagent.knowledge.SkillSearchRecord
import de.heckenmann.visualagent.knowledge.SkillStore
import de.heckenmann.visualagent.knowledge.SkillUpdateResult
import de.heckenmann.visualagent.protocol.SkillDocument
import de.heckenmann.visualagent.protocol.SkillPort
import de.heckenmann.visualagent.protocol.SkillSearchResult
import org.springframework.stereotype.Component
import de.heckenmann.visualagent.protocol.SkillCreateResult as ProtocolSkillCreateResult
import de.heckenmann.visualagent.protocol.SkillDeleteResult as ProtocolSkillDeleteResult
import de.heckenmann.visualagent.protocol.SkillUpdateResult as ProtocolSkillUpdateResult

/** Exposes the server-owned skill catalog to the Compose presentation. */
@Component
class SpringSkillPort(
    private val store: SkillStore,
) : SkillPort {
    override fun search(
        query: String,
        limit: Int,
    ): List<SkillSearchResult> = store.searchSkills(query, limit).map(SkillSearchRecord::toProtocol)

    override fun get(id: String): SkillDocument? = store.getSkill(id)?.toDocument()

    override fun create(
        title: String,
        content: String,
    ): ProtocolSkillCreateResult =
        when (val result = store.createSkill(title, content)) {
            is SkillCreateResult.Created -> ProtocolSkillCreateResult.Created(result.skill.toSearchResult())
            is SkillCreateResult.Duplicate -> ProtocolSkillCreateResult.Duplicate(result.skill.toSearchResult())
        }

    override fun update(
        id: String,
        expectedRevision: Long,
        title: String,
        content: String,
    ): ProtocolSkillUpdateResult =
        when (val result = store.updateSkill(id, expectedRevision, title, content)) {
            is SkillUpdateResult.Updated -> ProtocolSkillUpdateResult.Updated(result.skill.toSearchResult())
            is SkillUpdateResult.Conflict -> ProtocolSkillUpdateResult.Conflict(result.skill.toSearchResult())
            is SkillUpdateResult.Duplicate -> ProtocolSkillUpdateResult.Duplicate(result.skill.toSearchResult())
            SkillUpdateResult.NotFound -> ProtocolSkillUpdateResult.NotFound
        }

    override fun delete(
        id: String,
        expectedRevision: Long,
    ): ProtocolSkillDeleteResult =
        when (val result = store.deleteSkill(id, expectedRevision)) {
            is SkillDeleteResult.Deleted -> ProtocolSkillDeleteResult.Deleted(result.id, result.title, result.revision)
            is SkillDeleteResult.Conflict -> ProtocolSkillDeleteResult.Conflict(result.skill.toSearchResult())
            SkillDeleteResult.NotFound -> ProtocolSkillDeleteResult.NotFound
        }
}

private fun SkillRecord.toDocument(): SkillDocument = SkillDocument(toSearchResult(), content)

private fun SkillRecord.toSearchResult(): SkillSearchResult =
    SkillSearchResult(id, title, content.take(320), updatedAt.toString(), revision, readCount, lastReadAt?.toString())

private fun SkillSearchRecord.toProtocol(): SkillSearchResult =
    SkillSearchResult(id, title, snippet, updatedAt.toString(), revision, readCount, lastReadAt?.toString())
