package de.heckenmann.visualagent.ui.conversation.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.CapabilitySupport
import de.heckenmann.visualagent.protocol.ModelStatus
import de.heckenmann.visualagent.protocol.ProviderModel
import de.heckenmann.visualagent.protocol.ProviderProfile
import de.heckenmann.visualagent.protocol.capabilitySupport
import de.heckenmann.visualagent.ui.components.ActionIconButton

/** One row selectable in the composer; provider identity prevents duplicate model names colliding. */
internal data class ConversationModelOption(
    val provider: ProviderProfile,
    val model: ProviderModel,
    val favorite: Boolean,
)

/** Filters configured models without guessing capabilities or hiding unknown declarations. */
internal fun conversationModelOptions(
    profiles: List<ProviderProfile>,
    favorites: List<String>,
    query: String,
): List<ConversationModelOption> =
    profiles
        .filter { it.enabled }
        .flatMap { profile ->
            profile.models
                .filter {
                    it.status !in setOf(ModelStatus.DISABLED, ModelStatus.DEPRECATED) &&
                        it.id !in profile.modelBlacklist &&
                        (profile.modelWhitelist.isEmpty() || it.id in profile.modelWhitelist)
                }.map { model ->
                    ConversationModelOption(profile, model, model.id in favorites)
                }
        }.filter { query.isBlank() || "${it.provider.name} ${it.model.name} ${it.model.id}".contains(query.trim(), ignoreCase = true) }
        .sortedWith(compareByDescending<ConversationModelOption> { it.favorite }.thenBy { it.provider.name }.thenBy { it.model.name })

/** Compact searchable selector beside the user's next message, independent of panel-header visibility. */
@Composable
internal fun ConversationModelPicker(
    state: ConversationModelState,
    onManageProviders: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val profile = state.profiles.firstOrNull { it.id == state.selected.providerId }
    val model = profile?.models?.firstOrNull { it.id == state.selected.modelId }
    Column(Modifier.fillMaxWidth()) {
        Box {
            TextButton(
                onClick = {
                    query = ""
                    expanded = true
                },
                enabled = state.loaded && !state.busy,
                modifier = Modifier.semantics { contentDescription = "Select conversation model" },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    if (state.busy) {
                        "Updating model…"
                    } else {
                        "${model?.name ?: state.selected.modelId.ifBlank {
                            "Select model"
                        }} · ${profile?.name.orEmpty()}"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(Icons.Filled.ExpandMore, null, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.width(320.dp).heightIn(max = 440.dp)) {
                OutlinedTextField(
                    query,
                    onValueChange = { query = it },
                    label = { Text("Search models") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
                val options = conversationModelOptions(state.profiles, state.selected.favoriteModels, query)
                var lastGroup: String? = null
                options.forEach { option ->
                    val group = if (option.favorite) "Favorites" else option.provider.name
                    if (group != lastGroup) {
                        Text(
                            group,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        lastGroup = group
                    }
                    val supported = option.model.capabilitySupport("chat") != CapabilitySupport.UNSUPPORTED
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(option.model.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (supported) option.provider.name else "${option.provider.name} · Chat unsupported",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            },
                            onClick = {
                                state.select(option.provider.id, option.model.id)
                                expanded = false
                            },
                            enabled = supported && !state.busy,
                            leadingIcon = {
                                if (option.provider.id == state.selected.providerId &&
                                    option.model.id == state.selected.modelId
                                ) {
                                    Icon(Icons.Filled.Check, "Current model")
                                }
                            },
                            modifier =
                                Modifier.weight(1f).semantics {
                                    contentDescription =
                                        "Use ${option.provider.name}: ${option.model.id}"
                                },
                        )
                        ActionIconButton(
                            if (option.favorite) Icons.Filled.Star else Icons.Filled.StarOutline,
                            if (option.favorite) "Remove ${option.model.id} from favorites" else "Favorite ${option.model.id}",
                            enabled = !state.busy,
                            onClick = { state.favorite(option.model.id, !option.favorite) },
                        )
                    }
                }
                if (options.isEmpty()) Text("No matching models", modifier = Modifier.padding(12.dp))
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ActionIconButton(
                        Icons.Filled.Refresh,
                        "Refresh current provider models",
                        enabled = profile != null && !state.busy,
                        onClick = { profile?.let { state.refresh(it.id) } },
                    )
                    ActionIconButton(Icons.Filled.Settings, "Manage providers", onClick = {
                        expanded = false
                        onManageProviders()
                    })
                }
            }
        }
        state.error?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            if (!state.loaded) {
                ActionIconButton(Icons.Filled.Refresh, "Retry loading models", onClick = state::changed)
            }
        }
    }
}
