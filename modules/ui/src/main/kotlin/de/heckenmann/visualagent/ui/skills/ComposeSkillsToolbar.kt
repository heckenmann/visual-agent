package de.heckenmann.visualagent.ui.skills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.ui.components.ActionIconButton

/** Renders the skill search field and catalog actions. */
@Composable
internal fun SkillsToolbar(
    query: String,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onCreate: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            label = { Text("Search skills") },
            singleLine = true,
            isError = query.codePointCount(0, query.length) > MAX_QUERY_CODE_POINTS,
            supportingText = { Text("${query.codePointCount(0, query.length)}/$MAX_QUERY_CODE_POINTS") },
            modifier = Modifier.weight(1f),
        )
        ActionIconButton(Icons.Filled.Search, "Search skills", onSearch)
        ActionIconButton(Icons.Filled.Refresh, "Refresh skills", onSearch)
        ActionIconButton(Icons.Filled.Add, "Create skill", onCreate)
    }
}
