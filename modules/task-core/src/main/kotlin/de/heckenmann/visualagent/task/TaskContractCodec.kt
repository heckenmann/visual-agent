package de.heckenmann.visualagent.task

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI

/** Strict, bounded portable contracts; no dependency or provider-specific schema parser is needed. */
object TaskContractCodec {
    /** Maximum contract/result envelope size accepted before parsing. */
    const val MAX_CHARACTERS = 32_768

    /** Decodes an optional contract; invalid new data fails rather than silently losing requirements. */
    fun decode(text: String?): TaskContract? =
        text?.let {
            require(it.length <= MAX_CHARACTERS) { "Task contract exceeds the size limit" }
            Json.decodeFromString<TaskContract>(it).also(::validate)
        }

    /** Validates before serializing an immutable contract. */
    fun encode(contract: TaskContract): String {
        validate(contract)
        return Json.encodeToString(contract).also { require(it.length <= MAX_CHARACTERS) { "Task contract exceeds the size limit" } }
    }

    /** Checks reference integrity and bounded fields without inventing missing criteria. */
    fun validate(contract: TaskContract) {
        require(contract.version == 1 && contract.revision > 0) { "Unsupported task contract version or revision" }
        require(contract.objective.isNotBlank() && contract.objective.length <= 8_192) { "Task objective must be bounded and non-blank" }
        validateIds(contract.inputs.map { it.id })
        validateIds(contract.deliverables.map { it.id })
        validateIds(contract.criteria.map { it.id })
        require(contract.requiredOperations.size <= 32) { "Too many required operations" }
        require(contract.constraints.size <= 32 && contract.stopConditions.size <= 32) { "Too many task constraints" }
        require(
            (contract.constraints + contract.stopConditions).all { it.isNotBlank() && it.length <= 2_000 },
        ) { "Invalid task constraint" }
        contract.inputs.forEach { input ->
            require(input.path != null || input.sourceUrl != null || input.todoId != null) { "Task input needs a reference" }
            input.path?.let(::validatePath)
            input.sourceUrl?.let { url ->
                val uri = URI(url)
                require(url.length <= 2_000 && uri.scheme in setOf("http", "https") && uri.host != null && uri.userInfo == null) {
                    "Source URL must be HTTP(S) without credentials"
                }
            }
        }
        contract.deliverables.forEach { validatePath(it.path) }
        contract.criteria.forEach { criterion ->
            require(criterion.executionTool == null || validTool(criterion.executionTool)) { "Invalid criterion execution tool" }
            require(criterion.description.isNotBlank() && criterion.description.length <= 2_000) { "Invalid criterion description" }
            require(
                criterion.artifactId == null || contract.deliverables.any { it.id == criterion.artifactId },
            ) { "Unknown criterion artifact" }
            require(criterion.method == TaskCheckMethod.MANUAL || criterion.artifactId != null) { "Check requires an artifact" }
            require(criterion.expected == null || criterion.expected.length <= 4_000) { "Expected text exceeds the limit" }
            if (criterion.method in setOf(TaskCheckMethod.TEXT_CONTAINS, TaskCheckMethod.SOURCE_QUOTE)) {
                require(!criterion.expected.isNullOrBlank()) { "Text check requires expected text" }
            }
            if (criterion.method == TaskCheckMethod.SOURCE_QUOTE) {
                require(
                    contract.inputs.any { it.id == criterion.sourceInputId && it.path != null },
                ) { "Source quote requires a captured workspace input" }
            }
        }
        validatePolicy(contract.policy)
        contract.requiredOperations.forEach { require(validTool(it.tool)) { "Invalid required tool" } }
    }

    /** Restricts resources to normalized managed workspace paths, never arbitrary host paths. */
    fun validatePath(path: String) {
        require(
            path.isNotBlank() && path.length <= 1_000 && !path.startsWith('/') && '\\' !in path && ':' !in path,
        ) { "Invalid workspace path" }
        require(path.split('/').all { it.isNotBlank() && it != "." && it != ".." }) { "Workspace path must be normalized" }
    }

    private fun validateIds(ids: List<String>) {
        require(
            ids.size <= 32 &&
                ids.distinct().size == ids.size &&
                ids.all {
                    Regex("[A-Za-z0-9_-]{1,80}").matches(it)
                },
        ) { "Invalid or duplicate task IDs" }
    }

    private fun validatePolicy(policy: TaskPolicy) {
        require(policy.tools == null || policy.tools.size <= 64 && policy.tools.all(::validTool)) { "Invalid scoped tools" }
        require(policy.actions.size <= 64 && policy.actions.keys.all(::validTool)) { "Invalid scoped actions" }
        require(
            policy.actions.values.all {
                it.size <= 64 &&
                    it.all { action ->
                        Regex("[A-Za-z0-9_-]{1,80}").matches(action)
                    }
            },
        ) { "Invalid action restriction" }
        policy.resources?.let { resources ->
            require(resources.size <= 64) { "Too many scoped resources" }
            resources.forEach(::validatePath)
        }
    }

    private fun validTool(tool: String): Boolean = Regex("[a-z][a-z0-9:-]{0,79}").matches(tool)
}
