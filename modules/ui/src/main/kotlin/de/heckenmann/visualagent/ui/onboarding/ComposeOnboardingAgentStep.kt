package de.heckenmann.visualagent.ui.onboarding

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.OnboardingAgent
import de.heckenmann.visualagent.protocol.OnboardingAgentCreationAvailability

internal const val DEFAULT_RESEARCHER_REQUEST =
    "Create a Researcher sub-agent that can investigate topics, find reliable sources, " +
        "compare information, and summarize its findings clearly."

/** Renders the optional model-assisted first sub-agent creation step. */
@Composable
internal fun ColumnScope.OnboardingAgentStep(
    agents: List<OnboardingAgent>,
    description: String,
    creating: Boolean,
    resultMessage: String?,
    error: String?,
    availability: OnboardingAgentCreationAvailability?,
    onDescriptionChange: (String) -> Unit,
    onCreate: () -> Unit,
    onSelectModel: () -> Unit,
) {
    Text("Create your first sub-agent", style = MaterialTheme.typography.titleLarge)
    Text("Describe a helper for the main model to create. You can also do this later in the conversation.")
    availability?.takeUnless { it.available }?.let { unavailable ->
        Text(unavailable.reason.orEmpty(), color = MaterialTheme.colorScheme.error)
        if (unavailable.canSelectAnotherModel) {
            Button(onClick = onSelectModel) { Text("Choose another model") }
        }
    }
    if (agents.isNotEmpty()) {
        Text("Existing sub-agents", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth()) {
            items(agents) { agent ->
                ListItem(headlineContent = { Text(agent.name) }, supportingContent = { Text(agent.role) })
            }
        }
    }
    OutlinedTextField(
        value = description,
        onValueChange = onDescriptionChange,
        label = { Text("Describe the sub-agent") },
        supportingText = { Text("This text is sent to the main model as a normal conversation request.") },
        minLines = 2,
        maxLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = onCreate, enabled = description.isNotBlank() && !creating && availability?.available != false) {
        if (creating) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
        Text(if (creating) "Creating…" else "Ask model to create sub-agent")
    }
    resultMessage?.let { message ->
        Text(
            message,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
        )
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
