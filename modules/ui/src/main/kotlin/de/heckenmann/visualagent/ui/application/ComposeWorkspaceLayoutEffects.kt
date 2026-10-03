package de.heckenmann.visualagent.ui.application

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import de.heckenmann.visualagent.protocol.LayoutSize
import de.heckenmann.visualagent.protocol.LayoutWindowState
import de.heckenmann.visualagent.protocol.WorkspaceLayoutPort
import kotlinx.coroutines.flow.filterNotNull

/** Publishes geometry and persists panel changes sequentially without blocking composition. */
@Composable
internal fun WorkspaceLayoutEffects(
    port: WorkspaceLayoutPort,
    stage: LayoutSize,
    desktop: LayoutSize,
    windows: List<LayoutWindowState>,
    coordinator: WorkspaceLayoutPersistenceCoordinator = remember(port) { WorkspaceLayoutPersistenceCoordinator(port) },
) {
    SideEffect { coordinator.update(stage, desktop, windows) }
    LaunchedEffect(coordinator) {
        try {
            coordinator.updates.filterNotNull().collect { coordinator.publishLatest() }
        } finally {
            coordinator.flush()
        }
    }
}
