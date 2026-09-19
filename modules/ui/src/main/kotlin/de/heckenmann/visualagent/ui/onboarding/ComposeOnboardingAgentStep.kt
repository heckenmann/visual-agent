package de.heckenmann.visualagent.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.OnboardingAgent

internal const val DEFAULT_RESEARCHER_REQUEST =
    "Create a Researcher sub-agent that can investigate topics, find reliable sources, " +
        "compare information, and summarize its findings clearly."

/** Renders the optional model-assisted first sub-agent creation step. */
@Composable
internal fun OnboardingAgentStep(
    agents: List<OnboardingAgent>,
    description: String,
    creating: Boolean,
    resultMessage: String?,
    error: String?,
    onDescriptionChange: (String) -> Unit,
    onCreate: () -> Unit,
) {
    Text("Create your first sub-agent", style = MaterialTheme.typography.titleLarge)
    Text(
        "Describe the helper you want. The configured main model will inspect the existing agents and can create it " +
            "through the same agent:create flow used later in the conversation. This step is optional.",
    )
    Text("Existing sub-agents", style = MaterialTheme.typography.titleMedium)
    if (agents.isEmpty()) {
        Text("No sub-agents exist on this Visual Agent server yet.")
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            agents.forEach { agent ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(agent.name, style = MaterialTheme.typography.titleSmall)
                        Text(agent.role, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    OutlinedTextField(
        value = description,
        onValueChange = onDescriptionChange,
        label = { Text("Describe the sub-agent") },
        supportingText = { Text("This text is sent to the main model as a normal conversation request.") },
        minLines = 4,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = onCreate, enabled = description.isNotBlank() && !creating) {
        if (creating) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
        Text(if (creating) "Creating…" else "Ask model to create sub-agent")
    }
    resultMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
