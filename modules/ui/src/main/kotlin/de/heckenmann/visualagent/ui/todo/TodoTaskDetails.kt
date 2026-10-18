package de.heckenmann.visualagent.ui.todo

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import de.heckenmann.visualagent.task.TaskAttemptResult
import de.heckenmann.visualagent.task.TaskContract

/** Displays accepted and failed attempts with explicit evidence, without server-side reads in Compose. */
@Composable
internal fun TodoTaskDetails(
    contract: TaskContract?,
    attempts: List<TaskAttemptResult>,
    onOpenArtifact: ((String) -> Unit)? = null,
) {
    contract?.let {
        Text("Objective: ${it.objective}")
        Text("Acceptance criteria (revision ${it.revision})")
        it.criteria.forEach { criterion -> Text("• ${criterion.description}") }
        if (it.constraints.isNotEmpty()) Text("Constraints: ${it.constraints.joinToString("; ")}")
        if (it.stopConditions.isNotEmpty()) Text("Stop conditions: ${it.stopConditions.joinToString("; ")}")
    }
    attempts.forEach { attempt ->
        val acceptance =
            when {
                attempt.currentAccepted -> "Accepted result"
                attempt.accepted -> "Previously accepted result"
                else -> "Attempt only"
            }
        Text("Attempt ${attempt.attempt}: $acceptance · ${attempt.reported.outcome}")
        Text(attempt.reported.summary)
        attempt.artifacts.forEach { artifact ->
            if (onOpenArtifact != null) TextButton(onClick = { onOpenArtifact(artifact.path) }) { Text("Open ${artifact.path}") }
            Text("${artifact.path}: observed ${artifact.state} · ${artifact.sha256 ?: "No observed version"}")
            artifact.currentState?.let { Text("Current version: $it (historical acceptance refers to the recorded digest)") }
        }
        attempt.observations.forEach { check -> Text("${check.criterionId}: ${check.state} · ${check.detail}") }
        attempt.reported.sources.forEach { source -> Text("Source ${source.inputId}: ${source.claim}") }
        attempt.reported.blockers.forEach { blocker -> Text("Blocked: $blocker") }
        attempt.reviewVerdict?.let { Text("Independent review proposal: $it") }
        attempt.reviewFeedback?.let { Text("Review: $it") }
    }
}
