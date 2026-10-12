package de.heckenmann.visualagent.ui.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.task.TaskCheckMethod
import de.heckenmann.visualagent.task.TaskContract
import de.heckenmann.visualagent.task.TaskCriterion
import de.heckenmann.visualagent.task.TaskDeliverable
import de.heckenmann.visualagent.task.TaskInput
import de.heckenmann.visualagent.ui.components.PanelDropdownField
import de.heckenmann.visualagent.ui.components.PanelSelectOption

/** Human-readable contract editing without exposing JSON or changing existing policies implicitly. */
@Composable
internal fun TodoContractFields(
    contract: TaskContract?,
    objective: String,
    onChange: (TaskContract?) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked = contract != null, onCheckedChange = {
            onChange(if (it) TaskContract(objective = objective.ifBlank { "Define the objective" }) else null)
        })
        Text("Define acceptance criteria")
    }
    contract ?: return
    ContractText("Objective", contract.objective) { onChange(contract.copy(objective = it)) }
    contract.inputs.forEachIndexed { index, input ->
        ContractText("Input file ${index + 1}", input.path.orEmpty()) { path ->
            onChange(contract.copy(inputs = contract.inputs.mapIndexed { i, value -> if (i == index) value.copy(path = path) else value }))
        }
    }
    TextButton(onClick = {
        onChange(
            contract.copy(
                inputs =
                    contract.inputs + TaskInput("input-${contract.inputs.size + 1}", path = "input.txt"),
            ),
        )
    }, enabled = contract.inputs.size < 32) { Text("Add input file") }
    contract.deliverables.forEachIndexed { index, artifact ->
        ContractText("Output file ${index + 1}", artifact.path) { path ->
            onChange(
                contract.copy(
                    deliverables =
                        contract.deliverables.mapIndexed { i, value ->
                            if (i ==
                                index
                            ) {
                                value.copy(path = path)
                            } else {
                                value
                            }
                        },
                ),
            )
        }
    }
    TextButton(
        onClick = {
            onChange(
                contract.copy(
                    deliverables =
                        contract.deliverables +
                            TaskDeliverable(
                                "output-${contract.deliverables.size + 1}",
                                "Required output",
                                "output-${contract.deliverables.size + 1}.txt",
                            ),
                ),
            )
        },
        enabled =
            contract.deliverables.size < 32,
    ) { Text("Add output file") }
    contract.criteria.forEachIndexed { index, criterion ->
        /** Replaces this criterion while preserving sibling criteria. */
        fun update(value: TaskCriterion) {
            onChange(
                contract.copy(
                    criteria =
                        contract.criteria.mapIndexed { i, old ->
                            if (i ==
                                index
                            ) {
                                value
                            } else {
                                old
                            }
                        },
                ),
            )
        }
        ContractText("Acceptance criterion ${index + 1}", criterion.description) { update(criterion.copy(description = it)) }
        ContractText("Required verification tool (optional)", criterion.executionTool.orEmpty()) {
            update(criterion.copy(executionTool = it.ifBlank { null }))
        }
        PanelDropdownField(
            label = "Check",
            selectedValue = criterion.method.name,
            options = TaskCheckMethod.entries.map { PanelSelectOption(it.name, it.displayName()) },
            onSelected = { update(criterion.copy(method = TaskCheckMethod.valueOf(it))) },
        )
        PanelDropdownField(
            label = "Evidence file",
            selectedValue = criterion.artifactId.orEmpty(),
            options = listOf(PanelSelectOption("", "Select an output")) + contract.deliverables.map { PanelSelectOption(it.id, it.path) },
            onSelected = { update(criterion.copy(artifactId = it.ifBlank { null })) },
        )
        if (criterion.method == TaskCheckMethod.TEXT_CONTAINS || criterion.method == TaskCheckMethod.SOURCE_QUOTE) {
            ContractText("Expected text", criterion.expected.orEmpty()) { update(criterion.copy(expected = it)) }
        }
        if (criterion.method == TaskCheckMethod.SOURCE_QUOTE) {
            PanelDropdownField(
                label = "Source file",
                selectedValue = criterion.sourceInputId.orEmpty(),
                options =
                    listOf(PanelSelectOption("", "Select an input")) + contract.inputs.map { PanelSelectOption(it.id, it.path ?: it.id) },
                onSelected = { update(criterion.copy(sourceInputId = it.ifBlank { null })) },
            )
        }
        TextButton(onClick = {
            onChange(contract.copy(criteria = contract.criteria.filterNot { it.id == criterion.id }))
        }) { Text("Remove criterion") }
    }
    TextButton(onClick = {
        val id = "criterion-${java.util.UUID.randomUUID()}"
        onChange(
            contract.copy(criteria = contract.criteria + TaskCriterion(id, "Required content", contract.deliverables.firstOrNull()?.id)),
        )
    }, enabled = contract.criteria.size < 32) { Text("Add criterion") }
    ContractText("Constraints (one per line)", contract.constraints.joinToString("\n")) {
        onChange(contract.copy(constraints = it.lines().filter(String::isNotBlank)))
    }
    ContractText("Stop conditions (one per line)", contract.stopConditions.joinToString("\n")) {
        onChange(contract.copy(stopConditions = it.lines().filter(String::isNotBlank)))
    }
    Text("Restrict workspace resources for this task")
    Checkbox(checked = contract.policy.resources != null, onCheckedChange = {
        onChange(contract.copy(policy = contract.policy.copy(resources = if (it) emptyList() else null)))
    })
    ContractText(
        "Allowed workspace files or folders (one per line; explicit empty selection denies all)",
        contract.policy.resources
            .orEmpty()
            .joinToString("\n"),
    ) {
        onChange(
            contract.copy(
                policy =
                    contract.policy.copy(
                        resources =
                            it.lines().filter(String::isNotBlank),
                    ),
            ),
        )
    }
    TodoContractPolicyFields(contract, onChange)
    Text("Updating the contract stops the current attempt and requires a new review. Existing agent permissions still apply.")
}

@Composable
private fun ContractText(
    label: String,
    value: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
}

private fun TaskCheckMethod.displayName(): String =
    when (this) {
        TaskCheckMethod.FILE_EXISTS -> "File exists"
        TaskCheckMethod.TEXT_CONTAINS -> "Contains expected text"
        TaskCheckMethod.JSON_VALID -> "Valid JSON"
        TaskCheckMethod.SOURCE_QUOTE -> "Quote matches source"
        TaskCheckMethod.MANUAL -> "Independent content review"
    }
