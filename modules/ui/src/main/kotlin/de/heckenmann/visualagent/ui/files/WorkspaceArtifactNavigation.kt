package de.heckenmann.visualagent.ui.files

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.staticCompositionLocalOf

/** Existing Files panel location selected from todo evidence; no filesystem access on Main. */
internal val LocalWorkspaceArtifactPath = staticCompositionLocalOf<MutableState<String?>?> { null }

/** Activates the existing Files panel for an authorized managed artifact reference. */
internal val LocalOpenWorkspaceArtifact = staticCompositionLocalOf<((String) -> Unit)?> { null }

/** Shares artifact navigation and theme colors across existing workspace panels. */
@androidx.compose.runtime.Composable
internal fun WorkspaceArtifactNavigation(
    activateWindow: (String) -> Unit,
    darkTheme: Boolean,
    content: @androidx.compose.runtime.Composable () -> Unit,
) {
    val path = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalWorkspaceArtifactPath provides path,
        LocalOpenWorkspaceArtifact provides { selected ->
            path.value = selected
            activateWindow("files")
        },
        de.heckenmann.visualagent.ui.workspace.LocalSemanticActionColors provides
            de.heckenmann.visualagent.ui.workspace
                .semanticActionColors(darkTheme),
        content = content,
    )
}
