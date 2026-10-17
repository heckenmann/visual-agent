package de.heckenmann.visualagent.task

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Bounded planner proposal; child policy, inputs and requirements are inherited by the server. */
@Serializable
data class TaskDecomposition(
    val children: List<TaskChild>,
    val independent: Boolean = false,
) {
    /** Validates complete criterion coverage before constructing immutable child contracts. */
    fun contracts(
        parent: TaskContract,
        parentTodoId: String,
    ): List<TaskContract> {
        val normalize = { text: String -> text.trim().replace(Regex("\\s+"), " ").lowercase() }
        require(
            children.map { normalize(it.description) }.distinct().size == children.size,
        ) { "Equivalent tasks do not constitute progress" }
        require(
            children.none { normalize(it.description) == normalize(parent.objective) },
        ) { "A child must be more bounded than its parent" }
        require(independent) { "Dependent or unspecified packages must remain one task until dependency scheduling exists" }
        require(
            children
                .flatMap {
                    it.criterionIds
                }.distinct()
                .size == children.sumOf { it.criterionIds.size },
        ) { "Criteria cannot overlap between concurrent packages" }
        require(
            children.size in 2..8 &&
                children
                    .map {
                        it.description
                    }.distinct()
                    .size == children.size,
        ) { "Decomposition requires two to eight distinct tasks" }
        val ids = parent.criteria.map { it.id }.toSet()
        require(
            ids.isNotEmpty() &&
                children
                    .flatMap {
                        it.criterionIds
                    }.toSet() == ids,
        ) { "Every parent criterion must be covered, without unknown criteria" }
        require(
            parent.deliverables.all { artifact ->
                parent.criteria.any { it.artifactId == artifact.id }
            },
        ) { "Every deliverable needs a covered criterion" }
        val owners =
            parent.deliverables.map { artifact ->
                children.count { child -> parent.criteria.any { it.artifactId == artifact.id && it.id in child.criterionIds } }
            }
        require(owners.all { it == 1 }) { "Concurrent children cannot share output artifacts" }
        return children.map { child ->
            require(
                child.description.isNotBlank() && child.description.length <= 2_000 && child.criterionIds.isNotEmpty(),
            ) { "Invalid child task" }
            require(child.criterionIds.distinct().size == child.criterionIds.size) { "Duplicate child criterion" }
            val criteria = parent.criteria.filter { it.id in child.criterionIds }
            parent
                .copy(
                    objective = child.description,
                    revision = 1,
                    criteria = criteria,
                    deliverables = parent.deliverables.filter { artifact -> criteria.any { it.artifactId == artifact.id } },
                    parentTodoId = parentTodoId,
                    parentCriterionIds = child.criterionIds,
                ).also(TaskContractCodec::validate)
        }
    }

    /** Strict proposal decoding; callers keep the original executable task on any format failure. */
    companion object {
        /** Decodes a size-bounded model proposal. */
        fun parse(text: String): TaskDecomposition {
            require(text.length <= TaskContractCodec.MAX_CHARACTERS)
            return Json.decodeFromString(text)
        }
    }
}

/** One proposed child and its explicit coverage of the parent acceptance criteria. */
@Serializable
data class TaskChild(
    val description: String,
    val criterionIds: List<String>,
)
