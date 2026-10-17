package de.heckenmann.visualagent.ui.todo

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.heckenmann.visualagent.task.TaskContract

/** Optional narrowing controls; empty explicit selection denies access, omitted selection inherits. */
@Composable
internal fun TodoContractPolicyFields(
    contract: TaskContract,
    onChange: (TaskContract) -> Unit,
) {
    Text("Task access (existing global, agent and directory permissions still apply)")
    Text("Restrict tools for this task")
    Checkbox(checked = contract.policy.tools != null, onCheckedChange = {
        onChange(contract.copy(policy = contract.policy.copy(tools = if (it) emptyList() else null)))
    })
    contract.policy.tools?.let { tools ->
        OutlinedTextField(
            value = tools.joinToString("\n"),
            onValueChange = { onChange(contract.copy(policy = contract.policy.copy(tools = it.lines().filter(String::isNotBlank)))) },
            label = { Text("Allowed tool IDs (one per line; empty denies all)") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    OutlinedTextField(
        value =
            contract.policy.actions.entries
                .joinToString("\n") { (tool, actions) -> "$tool=${actions.joinToString(",")}" },
        onValueChange = { text ->
            val actions =
                text.lines().filter(String::isNotBlank).associate { line ->
                    line.substringBefore("=").trim() to
                        line
                            .substringAfter("=", "")
                            .split(",")
                            .map(String::trim)
                            .filter(String::isNotBlank)
                }
            onChange(contract.copy(policy = contract.policy.copy(actions = actions)))
        },
        label = { Text("Action restrictions: tool ID=action,action (empty action list denies that tool)") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = contract.reviewerAgentId.orEmpty(),
        onValueChange = { onChange(contract.copy(reviewerAgentId = it.takeIf(String::isNotBlank))) },
        label = { Text("Independent reviewer agent ID (optional)") },
        modifier = Modifier.fillMaxWidth(),
    )
    Text("Include existing agent history (default: isolated task context)")
    Checkbox(checked = contract.policy.includeAgentHistory, onCheckedChange = {
        onChange(contract.copy(policy = contract.policy.copy(includeAgentHistory = it)))
    })
}
