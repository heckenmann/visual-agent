package de.heckenmann.visualagent.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Renders onboarding navigation while preserving the optional agent-creation exit. */
@Composable
internal fun OnboardingNavigation(
    step: Int,
    automatic: Boolean,
    busy: Boolean,
    canContinue: Boolean,
    canSelectModel: Boolean,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onContinue: () -> Unit,
    onSkipAgents: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
    ) {
        if (step > 0 && step < AGENT_STEP) {
            OutlinedButton(onClick = onBack, enabled = !busy) { Text("Back") }
        }
        OutlinedButton(
            onClick = if (step == AGENT_STEP) onSkipAgents else onCancel,
            enabled = !busy,
        ) {
            Icon(Icons.Filled.Close, contentDescription = null)
            Text(
                if (step == AGENT_STEP) {
                    "Skip agent setup"
                } else if (automatic) {
                    "Skip setup"
                } else {
                    "Cancel"
                },
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Button(
            enabled = !busy && canContinue && canSelectModel,
            onClick = onContinue,
        ) {
            Icon(if (step >= REVIEW_STEP) Icons.Filled.Done else Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            Text(
                when (step) {
                    REVIEW_STEP -> if (busy) "Saving…" else "Save and continue"
                    AGENT_STEP -> "Finish"
                    else -> "Continue"
                },
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private const val REVIEW_STEP = 3
private const val AGENT_STEP = 4
