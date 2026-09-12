package de.heckenmann.visualagent.ui.skills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.SkillSearchResult
import de.heckenmann.visualagent.ui.components.PanelContentCard
import de.heckenmann.visualagent.ui.components.PanelEmptyState
import de.heckenmann.visualagent.ui.components.RegisterPanelVerticalScrollbar

/** Renders the searchable catalog rows while preserving the panel scroll position. */
@Composable
internal fun SkillsCatalogList(
    skills: List<SkillSearchResult>,
    selectedId: String?,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    RegisterPanelVerticalScrollbar(scrollState)
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (skills.isEmpty()) {
            PanelEmptyState(
                "No skills found",
                "Save stable, reusable Markdown results here for later model work, then open one to view or edit it.",
            )
        } else {
            skills.forEach { skill ->
                PanelContentCard(
                    modifier = Modifier.clickable { onSelect(skill.id) },
                    backgroundColor =
                        if (selectedId == skill.id) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
                        } else {
                            null
                        },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(skill.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Revision ${skill.revision} · ${skill.readCount} model reads · Updated ${skill.updatedAt}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                skill.snippet.ifBlank { "No matching excerpt" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
    }
}
