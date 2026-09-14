package de.heckenmann.visualagent.desktop

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val StartupServerRowShape = RoundedCornerShape(16.dp)

/** Renders client-local Visual Agent server bookmarks before a server is connected. */
@Composable
internal fun ComposeStartupServerBookmarks(
    loadResult: DesktopServerBookmarkLoadResult,
    onStartLocal: () -> Unit,
    onCreateServer: () -> Unit,
    onEditServer: (DesktopServerBookmark) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = (loadResult as? DesktopServerBookmarkLoadResult.Loaded)?.state ?: DesktopServerBookmarkState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier.fillMaxWidth()) {
        Text("Connect to a server", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Choose where Visual Agent runs. LLM providers are configured after the connection is ready.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = LOCAL_SERVER_ID) {
                ComposeStartupServerRow(
                    icon = Icons.Filled.Computer,
                    title = "Local",
                    subtitle = "Run Visual Agent on this device",
                    selected = true,
                    remote = false,
                    onClick = onStartLocal,
                )
            }
            items(state.visualAgentServerBookmarks, key = DesktopServerBookmark::id) { bookmark ->
                ComposeStartupServerRow(
                    icon = Icons.Filled.Cloud,
                    title = bookmark.name,
                    subtitle = bookmark.visualAgentServerEndpoint,
                    selected = false,
                    remote = true,
                    onClick = { onEditServer(bookmark) },
                )
            }
        }
        when (loadResult) {
            is DesktopServerBookmarkLoadResult.Invalid ->
                Text(loadResult.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            is DesktopServerBookmarkLoadResult.Loaded -> Unit
        }
        androidx.compose.material3.OutlinedButton(onClick = onCreateServer, modifier = Modifier.align(Alignment.End)) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add server")
        }
    }
}

@Composable
private fun ComposeStartupServerRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    remote: Boolean,
    onClick: () -> Unit,
) {
    val cardColors =
        if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        }
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = StartupServerRowShape,
                ).clickable(onClick = onClick)
                .semantics {
                    contentDescription =
                        if (remote) {
                            "$title remote Visual Agent server bookmark. Connection unavailable; select to edit."
                        } else {
                            "$title Visual Agent server"
                        }
                },
        shape = StartupServerRowShape,
        colors = cardColors,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Surface(shape = StartupServerRowShape, color = MaterialTheme.colorScheme.primary) {
                    Text(
                        "Start",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Connection available later",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
