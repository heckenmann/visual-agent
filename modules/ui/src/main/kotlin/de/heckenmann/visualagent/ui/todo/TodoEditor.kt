package de.heckenmann.visualagent.ui.todo

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.heckenmann.visualagent.protocol.AgentSummary
import de.heckenmann.visualagent.protocol.TodoItem
import de.heckenmann.visualagent.protocol.TodoState
import de.heckenmann.visualagent.ui.components.PanelDropdownField
import de.heckenmann.visualagent.ui.components.PanelSelectOption
import de.heckenmann.visualagent.ui.components.labelizeEnumName
import de.heckenmann.visualagent.ui.modal.ModalDialogLayout
import de.heckenmann.visualagent.ui.modal.modalSaveButton
import de.heckenmann.visualagent.ui.modal.modalSecondaryButton

@Composable
internal fun TodoEditor(
    todo: TodoItem,
    agents: List<AgentSummary>,
    onCancel: () -> Unit,
    onSave: (String, TodoState, String?) -> Unit = { _, _, _ -> },
    onSaveContract: ((String, TodoState, String?, de.heckenmann.visualagent.task.TaskContract?) -> Unit)? = null,
) {
    var contract by remember(todo.id) { mutableStateOf(todo.contract) }
    var description by remember(todo.id) { mutableStateOf(todo.description) }
    var status by remember(todo.id) { mutableStateOf(todo.status) }
    var assignedAgentId by remember(todo.id) { mutableStateOf(todo.assignedAgentId ?: UNASSIGNED_AGENT_ID) }
    val agentOptions =
        listOf(PanelSelectOption(UNASSIGNED_AGENT_ID, "Unassigned")) +
            agents.map { PanelSelectOption(it.id, it.name) }
    ModalDialogLayout(
        body = {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            if (onSaveContract != null) TodoContractFields(contract, description) { contract = it }
            PanelDropdownField(
                label = "Status",
                selectedValue = status.name,
                options = TodoState.entries.map { PanelSelectOption(it.name, it.name.labelizeEnumName()) },
                onSelected = { status = TodoState.valueOf(it) },
            )
            PanelDropdownField(
                label = "Assigned agent",
                selectedValue = assignedAgentId,
                options = agentOptions,
                onSelected = { assignedAgentId = it },
            )
        },
        footer = {
            modalSecondaryButton(label = "Cancel", onClick = onCancel)
            modalSaveButton(
                label = "Save todo",
                enabled =
                    description.isNotBlank() &&
                        (
                            contract == null ||
                                runCatching {
                                    de.heckenmann.visualagent.task.TaskContractCodec.validate(
                                        contract!!,
                                    )
                                }.isSuccess
                        ),
                onClick = {
                    val selectedAgent = assignedAgentId.takeIf { it != UNASSIGNED_AGENT_ID }
                    if (onSaveContract == null) {
                        onSave(description.trim(), status, selectedAgent)
                    } else {
                        onSaveContract(description.trim(), status, selectedAgent, contract)
                    }
                },
            )
        },
    )
}

internal const val UNASSIGNED_AGENT_ID = "__unassigned__"
