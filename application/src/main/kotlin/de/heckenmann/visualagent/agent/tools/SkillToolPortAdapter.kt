package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.SkillToolPort
import de.heckenmann.visualagent.agent.tools.api.ToolSkillCreateResult
import de.heckenmann.visualagent.agent.tools.api.ToolSkillDeleteResult
import de.heckenmann.visualagent.agent.tools.api.ToolSkillDocument
import de.heckenmann.visualagent.agent.tools.api.ToolSkillSearchResult
import de.heckenmann.visualagent.agent.tools.api.ToolSkillSummary
import de.heckenmann.visualagent.agent.tools.api.ToolSkillUpdateResult
import de.heckenmann.visualagent.knowledge.SkillCreateResult
import de.heckenmann.visualagent.knowledge.SkillDeleteResult
import de.heckenmann.visualagent.knowledge.SkillRecord
import de.heckenmann.visualagent.knowledge.SkillSearchRecord
import de.heckenmann.visualagent.knowledge.SkillStore
import de.heckenmann.visualagent.knowledge.SkillUpdateResult
import org.springframework.stereotype.Component

/** Adapts application-owned skill persistence to the provider-neutral tool contract. */
@Component
class SkillToolPortAdapter(
    private val store: SkillStore,
) : SkillToolPort {
    override fun create(
        title: String,
        content: String,
    ): ToolSkillCreateResult =
        when (val result = store.createSkill(title, content)) {
            is SkillCreateResult.Created -> ToolSkillCreateResult.Created(result.skill.toToolSummary())
            is SkillCreateResult.Duplicate -> ToolSkillCreateResult.Duplicate(result.skill.toToolSummary())
        }

    override fun search(
        query: String,
        limit: Int,
    ): List<ToolSkillSearchResult> = store.searchSkills(query, limit).map(SkillSearchRecord::toToolResult)

    override fun read(
        id: String,
        isCancelled: () -> Boolean,
    ): ToolSkillDocument? = store.readSkill(id, isCancelled)?.let { ToolSkillDocument(it.toToolSummary(), it.content) }

    override fun update(
        id: String,
        expectedRevision: Long,
        title: String,
        content: String,
    ): ToolSkillUpdateResult =
        when (val result = store.updateSkill(id, expectedRevision, title, content)) {
            is SkillUpdateResult.Updated -> ToolSkillUpdateResult.Updated(result.skill.toToolSummary())
            is SkillUpdateResult.Conflict -> ToolSkillUpdateResult.Conflict(result.skill.toToolSummary())
            is SkillUpdateResult.Duplicate -> ToolSkillUpdateResult.Duplicate(result.skill.toToolSummary())
            SkillUpdateResult.NotFound -> ToolSkillUpdateResult.NotFound
        }

    override fun delete(
        id: String,
        expectedRevision: Long,
    ): ToolSkillDeleteResult =
        when (val result = store.deleteSkill(id, expectedRevision)) {
            is SkillDeleteResult.Deleted ->
                ToolSkillDeleteResult.Deleted(
                    result.id,
                    result.title,
                    result.revision,
                    result.deletedAt.toString(),
                )
            is SkillDeleteResult.Conflict -> ToolSkillDeleteResult.Conflict(result.skill.toToolSummary())
            SkillDeleteResult.NotFound -> ToolSkillDeleteResult.NotFound
        }
}

private fun SkillRecord.toToolSummary(): ToolSkillSummary =
    ToolSkillSummary(id, title, createdAt.toString(), updatedAt.toString(), revision, readCount, lastReadAt?.toString())

private fun SkillSearchRecord.toToolResult(): ToolSkillSearchResult =
    ToolSkillSearchResult(id, title, snippet, updatedAt.toString(), revision, readCount, lastReadAt?.toString())
