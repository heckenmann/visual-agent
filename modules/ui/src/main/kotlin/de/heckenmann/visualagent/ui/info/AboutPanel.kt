package de.heckenmann.visualagent.ui.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ApplicationInformation
import de.heckenmann.visualagent.protocol.ApplicationInformationPort
import de.heckenmann.visualagent.protocol.OpenSourceLicensePort
import de.heckenmann.visualagent.protocol.ProjectMetadata
import de.heckenmann.visualagent.protocol.ProjectResourceLink
import de.heckenmann.visualagent.ui.components.ActionIconButton
import de.heckenmann.visualagent.ui.workspace.ComposeWorkspaceBrand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shows project identity, current runtime information, project resources, and bundled licenses. */
@Composable
internal fun AboutPanel(
    informationPort: ApplicationInformationPort,
    licensePort: OpenSourceLicensePort,
) {
    var information by remember(informationPort) { mutableStateOf<ApplicationInformation?>(null) }
    var informationError by remember(informationPort) { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(informationPort) {
        runCatching { withContext(Dispatchers.IO) { informationPort.current() } }
            .onSuccess { information = it }
            .onFailure { informationError = true }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComposeWorkspaceBrand()
            Column(Modifier.weight(1f)) {
                Text(ProjectMetadata.DISPLAY_NAME, style = MaterialTheme.typography.headlineSmall)
                Text(
                    information?.let { "Version ${it.version}" } ?: if (informationError) "Version unavailable" else "Loading version…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == OVERVIEW_TAB,
                onClick = { selectedTab = OVERVIEW_TAB },
                text = { Text("Overview") },
            )
            Tab(
                selected = selectedTab == LICENSES_TAB,
                onClick = { selectedTab = LICENSES_TAB },
                text = { Text("Open-source licenses") },
            )
        }
        Box(Modifier.weight(1f)) {
            when (selectedTab) {
                OVERVIEW_TAB ->
                    when {
                        information != null ->
                            ProjectOverview(
                                information = requireNotNull(information),
                                onViewLicense = { selectedTab = LICENSES_TAB },
                            )
                        informationError ->
                            Text(
                                "Application information could not be loaded.",
                                Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.error,
                            )
                    }
                LICENSES_TAB -> OpenSourceLicensesPanel(licensePort)
            }
        }
    }
}

@Composable
private fun ProjectOverview(
    information: ApplicationInformation,
    onViewLicense: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(ProjectMetadata.DESCRIPTION, style = MaterialTheme.typography.bodyMedium)
        Text("Project", style = MaterialTheme.typography.titleMedium)
        ProjectLinks(ProjectMetadata.projectLinks()) { link -> uriHandler.openUri(link.url) }
        Text("Community", style = MaterialTheme.typography.titleMedium)
        ProjectLinks(ProjectMetadata.communityLinks()) { link -> uriHandler.openUri(link.url) }
        Text("Legal", style = MaterialTheme.typography.titleMedium)
        ResourceRow(
            title = "Visual Agent license",
            url = null,
            onOpen = onViewLicense,
            icon = Icons.Filled.Description,
            actionDescription = "View Visual Agent license",
        )
        Text("Runtime", style = MaterialTheme.typography.titleMedium)
        Text(
            "${information.runtimeName} ${information.runtimeVersion}\n" +
                "${information.operatingSystem} ${information.operatingSystemVersion} · ${information.architecture}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProjectLinks(
    links: List<ProjectResourceLink>,
    onOpen: (ProjectResourceLink) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        links.forEach { link ->
            ResourceRow(title = link.title, url = link.url, onOpen = { onOpen(link) })
        }
    }
}

@Composable
private fun ResourceRow(
    title: String,
    url: String?,
    onOpen: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.AutoMirrored.Filled.OpenInNew,
    actionDescription: String = "Open $title",
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (!url.isNullOrBlank()) {
                Text(
                    url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ActionIconButton(icon = icon, description = actionDescription, onClick = onOpen)
    }
}

private const val OVERVIEW_TAB = 0
private const val LICENSES_TAB = 1
