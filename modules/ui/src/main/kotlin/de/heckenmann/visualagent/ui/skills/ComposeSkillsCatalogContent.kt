package de.heckenmann.visualagent.ui.skills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.SkillSearchResult

/** Composes the skill search toolbar, catalog list, and status line. */
@Composable
internal fun SkillsCatalogContent(
    query: String,
    skills: List<SkillSearchResult>,
    selectedId: String?,
    status: String,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onCreate: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val listScrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SkillsToolbar(query, onQueryChanged, onSearch, onCreate)
        SkillsCatalogList(
            skills = skills,
            selectedId = selectedId,
            scrollState = listScrollState,
            modifier = Modifier.weight(1f),
            onSelect = onSelect,
        )
        Text(status, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
    }
}
