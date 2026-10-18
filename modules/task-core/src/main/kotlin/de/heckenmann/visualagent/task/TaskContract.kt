package de.heckenmann.visualagent.task

import kotlinx.serialization.Serializable

/** Immutable execution and acceptance contract shared by server, tools and presentation. */
@Serializable
data class TaskContract(
    val version: Int = 1,
    val revision: Long = 1,
    val objective: String,
    val inputs: List<TaskInput> = emptyList(),
    val deliverables: List<TaskDeliverable> = emptyList(),
    val criteria: List<TaskCriterion> = emptyList(),
    val constraints: List<String> = emptyList(),
    val stopConditions: List<String> = emptyList(),
    val requiredOperations: List<TaskOperation> = emptyList(),
    val policy: TaskPolicy = TaskPolicy(),
    val reviewerAgentId: String? = null,
    val parentTodoId: String? = null,
    val parentCriterionIds: List<String> = emptyList(),
)

/** Explicitly selected input; source documents never become system instructions. */
@Serializable
data class TaskInput(
    val id: String,
    val path: String? = null,
    val sourceUrl: String? = null,
    val todoId: String? = null,
)

/** Artifact that a worker is expected to produce in the managed workspace. */
@Serializable
data class TaskDeliverable(
    val id: String,
    val description: String,
    val path: String,
)

/** Server-supported check for one criterion; MANUAL remains unchecked until independent inspection. */
@Serializable
enum class TaskCheckMethod {
    FILE_EXISTS,
    TEXT_CONTAINS,
    JSON_VALID,
    SOURCE_QUOTE,
    MANUAL,
}

/** Measurable requirement; expected text is data, not an executable expression. */
@Serializable
data class TaskCriterion(
    val id: String,
    val description: String,
    val artifactId: String? = null,
    val method: TaskCheckMethod = TaskCheckMethod.MANUAL,
    val expected: String? = null,
    val sourceInputId: String? = null,
    val required: Boolean = true,
    val executionTool: String? = null,
)

/** Explicit capability requirement, using stable internal tool identities. */
@Serializable
data class TaskOperation(
    val tool: String,
    val action: String? = null,
)

/** Optional narrowing; null means inherit, while an empty collection denies every member. */
@Serializable
data class TaskPolicy(
    val tools: List<String>? = null,
    val actions: Map<String, List<String>> = emptyMap(),
    val resources: List<String>? = null,
    val includeAgentHistory: Boolean = false,
)
