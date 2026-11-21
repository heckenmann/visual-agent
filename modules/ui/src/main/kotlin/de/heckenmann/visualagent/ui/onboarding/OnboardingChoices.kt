package de.heckenmann.visualagent.ui.onboarding

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/** Displays choices in a bounded, independently scrolling selection list. */
@Composable
internal fun <T> OnboardingChoices(
    choices: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxWidth().selectableGroup()) {
        items(choices) { choice ->
            val checked = choice == selected
            ListItem(
                headlineContent = { Text(label(choice)) },
                leadingContent = { RadioButton(checked, onClick = null) },
                modifier = Modifier.selectable(checked, role = Role.RadioButton) { onSelect(choice) },
            )
        }
    }
}
