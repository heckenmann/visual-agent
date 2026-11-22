package de.heckenmann.visualagent.server

import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.protocol.TaskModelAssignment

/** Converts persisted task model defaults to their protocol representation. */
internal fun ProviderCatalogService.taskModelAssignmentsToProtocol(): List<TaskModelAssignment> =
    taskModelAssignments().map { (capability, selection) -> TaskModelAssignment(capability, selection.first, selection.second) }

/** Replaces task model defaults from protocol values with duplicate-capability validation. */
internal fun ProviderCatalogService.replaceTaskModelAssignmentsFromProtocol(
    assignments: List<TaskModelAssignment>,
    expectedAssignments: List<TaskModelAssignment>,
) = replaceTaskModelAssignments(assignments.toApplicationAssignments(), expectedAssignments.toApplicationAssignments())

/** Converts unique protocol task assignments to the provider catalog's map representation. */
internal fun List<TaskModelAssignment>.toApplicationAssignments(): Map<String, Pair<String, String>> {
    require(map(TaskModelAssignment::capability).distinct().size == size) { "Task capabilities must be unique" }
    return associate { it.capability to (it.providerId to it.modelId) }
}
