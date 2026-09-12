package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.agent.tools.api.SkillToolPort
import de.heckenmann.visualagent.agent.tools.api.ToolDefinition
import de.heckenmann.visualagent.agent.tools.api.ToolId
import de.heckenmann.visualagent.agent.tools.api.ToolResult
import de.heckenmann.visualagent.agent.tools.api.ToolSkillCreateResult
import de.heckenmann.visualagent.agent.tools.api.ToolSkillDeleteResult
import de.heckenmann.visualagent.agent.tools.api.ToolSkillDocument
import de.heckenmann.visualagent.agent.tools.api.ToolSkillSearchResult
import de.heckenmann.visualagent.agent.tools.api.ToolSkillSummary
import de.heckenmann.visualagent.agent.tools.api.ToolSkillUpdateResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Lets enabled agents persist and reuse bounded, searchable Markdown skills. */
@AgentTool
class SkillsTool(
    private val skills: SkillToolPort,
) : VisualAgentTool {
    override val definition =
        ToolDefinition(
            id = ToolId("skills"),
            name = ToolId("skills").toFunctionName(),
            description =
                "Manage durable reusable Markdown skills. " +
                    "Skills are database records, not workspace files: never create SKILL.md or another skill document " +
                    "with workspace:file, javascript:execute, or terminal. Handle skill requests with this tool directly; " +
                    "Search before expensive work when a prior solution may exist. " +
                    "After substantial completed work, save a self-contained stable result with a specific title, " +
                    "rationale, commands, and reusable code patterns. " +
                    "Do not store secrets, credentials, PII, transient progress, or raw provider responses. " +
                    "Actions: create {action, title, content}; search {action, query, limit}; get {action, id}; " +
                    "update {action, id, expectedRevision, title, content}; delete {action, id, expectedRevision}. " +
                    "Use the revision returned by search/get for update and delete. Search returns bounded snippets; get returns complete Markdown.",
            inputSchema = SKILLS_SCHEMA,
        )

    override fun execute(
        inputJson: String,
        context: Map<String, Any>,
    ): ToolResult {
        val input = parseObject(inputJson)
        val action = input.requiredString("action").lowercase()
        val cancellation = context["toolCancellationToken"] as? ToolCancellationToken
        return runCatching {
            when (action) {
                "create" -> create(input)
                "search" -> search(input)
                "get" -> get(input, cancellation)
                "update" -> update(input)
                "delete" -> delete(input)
                else -> failure("skills", "Unsupported action '$action'. Use create, search, get, update, or delete.")
            }
        }.getOrElse { error ->
            if (cancellation?.isCancelled == true || error is InterruptedException) {
                failure("skills", "TOOL_CANCELLED: Skill operation was cancelled.")
            } else {
                failure("skills", "TOOL_ARGUMENTS: ${error.message ?: "Invalid skills tool arguments."}")
            }
        }
    }

    private fun create(input: kotlinx.serialization.json.JsonObject): ToolResult =
        when (val result = skills.create(input.requiredString("title"), input.requiredString("content"))) {
            is ToolSkillCreateResult.Created -> dataSuccess("create", result.skill)
            is ToolSkillCreateResult.Duplicate -> dataSuccess("duplicate", result.skill)
        }

    private fun search(input: kotlinx.serialization.json.JsonObject): ToolResult {
        val query = input.string("query").orEmpty()
        val limit = (input.int("limit") ?: 5).coerceIn(1, 25)
        return dataSuccess("search", skills.search(query, limit))
    }

    private fun get(
        input: kotlinx.serialization.json.JsonObject,
        cancellation: ToolCancellationToken?,
    ): ToolResult {
        val document =
            skills.read(input.requiredString("id")) { cancellation?.isCancelled == true || Thread.currentThread().isInterrupted }
                ?: return failure("skills", "NOT_FOUND: Skill was not found.")
        return dataSuccess("get", document)
    }

    private fun update(input: kotlinx.serialization.json.JsonObject): ToolResult {
        val revision =
            input["expectedRevision"]?.jsonPrimitive?.longOrNull
                ?: error("expectedRevision must be a number")
        return when (
            val result =
                skills.update(
                    input.requiredString("id"),
                    revision,
                    input.requiredString("title"),
                    input.requiredString("content"),
                )
        ) {
            is ToolSkillUpdateResult.Updated -> dataSuccess("update", result.skill)
            is ToolSkillUpdateResult.Conflict -> failure("skills", "CONFLICT: Skill revision is stale. Read it again before updating.")
            is ToolSkillUpdateResult.Duplicate ->
                failure(
                    "skills",
                    "DUPLICATE: Equivalent skill already exists with id ${result.skill.id}.",
                )
            ToolSkillUpdateResult.NotFound -> failure("skills", "NOT_FOUND: Skill was not found.")
        }
    }

    private fun delete(input: kotlinx.serialization.json.JsonObject): ToolResult {
        val revision =
            input["expectedRevision"]?.jsonPrimitive?.longOrNull
                ?: error("expectedRevision must be a number")
        return when (val result = skills.delete(input.requiredString("id"), revision)) {
            is ToolSkillDeleteResult.Deleted -> dataSuccess("delete", result)
            is ToolSkillDeleteResult.Conflict -> failure("skills", "CONFLICT: Skill revision is stale. Read it again before deleting.")
            ToolSkillDeleteResult.NotFound -> failure("skills", "NOT_FOUND: Skill was not found.")
        }
    }

    private fun dataSuccess(
        action: String,
        value: Any,
    ): ToolResult {
        val data: JsonElement =
            when (value) {
                is ToolSkillSummary ->
                    buildJsonObject {
                        put("action", action)
                        put("skill", json.encodeToJsonElement(value))
                    }
                is ToolSkillDocument ->
                    buildJsonObject {
                        put("action", action)
                        put("skill", json.encodeToJsonElement(value))
                    }
                is List<*> ->
                    buildJsonObject {
                        put("action", action)
                        put("results", json.encodeToJsonElement(value.filterIsInstance<ToolSkillSearchResult>()))
                    }
                is ToolSkillDeleteResult.Deleted ->
                    buildJsonObject {
                        put("action", action)
                        put("id", value.id)
                        put("title", value.title)
                        put("revision", value.revision)
                        put("deletedAt", value.deletedAt)
                    }
                else -> error("Unsupported skill result")
            }
        return ToolResult("skills", true, "", data = data)
    }

    private companion object {
        const val SKILLS_SCHEMA =
            """{"type":"object","properties":{"action":{"type":"string","enum":["create","search","get","update","delete"]},"title":{"type":"string","minLength":1,"maxLength":200},"content":{"type":"string","minLength":1,"maxLength":120000},"query":{"type":"string","maxLength":500},"limit":{"type":"integer","minimum":1,"maximum":25},"id":{"type":"string","format":"uuid"},"expectedRevision":{"type":"integer","minimum":1}},"required":["action"],"additionalProperties":false}"""
    }
}
