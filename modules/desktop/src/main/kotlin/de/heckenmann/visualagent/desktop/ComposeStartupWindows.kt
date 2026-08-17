@file:Suppress("FunctionName")

package de.heckenmann.visualagent.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import de.heckenmann.visualagent.AppIdentity
import de.heckenmann.visualagent.protocol.LayoutSize
import de.heckenmann.visualagent.protocol.LayoutWindowState
import de.heckenmann.visualagent.protocol.WorkspaceLayoutSnapshot
import de.heckenmann.visualagent.ui.application.ComposeApplicationDependencies
import de.heckenmann.visualagent.ui.application.StartupPhase
import de.heckenmann.visualagent.ui.application.StartupStatus
import de.heckenmann.visualagent.ui.application.VisualAgentComposeApp
import de.heckenmann.visualagent.ui.workspace.visualAgentDarkColorScheme
import de.heckenmann.visualagent.ui.workspace.visualAgentTypography

/** Identifies which native window the desktop host must currently render. */
internal enum class StartupWindowMode {
    /** The server is still starting or startup failed. */
    SPLASH,

    /** The server is ready and the persisted workspace can be shown. */
    MAIN,
}

/**
 * Selects the startup window without allowing a partially initialized main window.
 *
 * @param status Current server bootstrap status
 * @param dependencies Protocol dependencies loaded from the server
 * @return The only window that may be created for the current startup state
 */
internal fun startupWindowMode(
    status: StartupStatus,
    dependencies: ComposeApplicationDependencies?,
): StartupWindowMode =
    if (status.phase == StartupPhase.READY && dependencies != null) {
        StartupWindowMode.MAIN
    } else {
        StartupWindowMode.SPLASH
    }

/** Renders the independent, centered, frameless startup window. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ComposeStartupSplashWindow(
    status: StartupStatus,
    onRetry: () -> Unit,
    onCloseRequest: () -> Unit,
) {
    val windowState =
        rememberWindowState(
            width = DEFAULT_SPLASH_WIDTH,
            height = DEFAULT_SPLASH_HEIGHT,
            position = WindowPosition.Aligned(Alignment.Center),
        )
    Window(
        onCloseRequest = onCloseRequest,
        title = "$STARTUP_WINDOW_TITLE – Starting",
        icon = @Suppress("DEPRECATION") painterResource("icons/visual-agent.png"),
        state = windowState,
        decoration = WindowDecoration.Undecorated(),
        resizable = false,
    ) {
        ComposeStartupSplash(status = status, onRetry = onRetry)
    }
}

/** Renders the main window with its own state and independent lifecycle. */
@Composable
internal fun ComposeMainWindow(
    dependencies: ComposeApplicationDependencies,
    persistedLayout: WorkspaceLayoutSnapshot,
    persistedWindows: List<LayoutWindowState>,
    onCloseApplication: (WindowState) -> Unit,
) {
    val initialStage = persistedLayout.stage
    val initialPosition = persistedLayout.stagePosition
    var geometryRestored by remember { mutableStateOf(false) }
    val windowState =
        rememberWindowState(
            width = initialStage?.width?.dp ?: DEFAULT_MAIN_WINDOW_WIDTH,
            height = initialStage?.height?.dp ?: DEFAULT_MAIN_WINDOW_HEIGHT,
            position =
                initialPosition?.let { WindowPosition.Absolute(it.x.dp, it.y.dp) }
                    ?: WindowPosition.Aligned(Alignment.Center),
        )
    Window(
        visible = geometryRestored,
        onCloseRequest = { onCloseApplication(windowState) },
        title = STARTUP_WINDOW_TITLE,
        icon = @Suppress("DEPRECATION") painterResource("icons/visual-agent.png"),
        state = windowState,
    ) {
        LaunchedEffect(persistedLayout) {
            restoreMainWindowGeometry(windowState, persistedLayout, currentScreenBounds())
            geometryRestored = true
        }
        VisualAgentComposeApp(
            deps = dependencies,
            onCloseApplication = { onCloseApplication(windowState) },
            persistedWindows = persistedWindows,
        )
    }
}

/** Applies persisted main-window geometry and corrects positions outside the current screen. */
internal fun restoreMainWindowGeometry(
    windowState: WindowState,
    persistedLayout: WorkspaceLayoutSnapshot,
    screenBounds: ScreenBounds?,
) {
    persistedLayout.stage?.let { stage ->
        windowState.size = DpSize(stage.width.dp, stage.height.dp)
    }
    persistedLayout.stagePosition?.let { position ->
        val size = windowState.size
        val restored =
            screenBounds?.let { bounds ->
                clampWindowPosition(
                    position,
                    LayoutSize(size.width.value.toDouble(), size.height.value.toDouble()),
                    bounds,
                )
            } ?: position
        windowState.position = WindowPosition.Absolute(restored.x.dp, restored.y.dp)
    }
}

/** Current screen bounds exposed by Compose's desktop window host. */
private fun FrameWindowScope.currentScreenBounds(): ScreenBounds? {
    val configuration = window.graphicsConfiguration ?: return null
    val transform = configuration.defaultTransform
    val bounds = configuration.bounds
    return ScreenBounds(
        x = bounds.x / transform.scaleX,
        y = bounds.y / transform.scaleY,
        width = bounds.width / transform.scaleX,
        height = bounds.height / transform.scaleY,
    )
}

/** Renders startup progress and the actionable retry state inside the splash window. */
@Composable
internal fun ComposeStartupSplash(
    status: StartupStatus,
    onRetry: () -> Unit,
) {
    MaterialTheme(
        colorScheme = visualAgentDarkColorScheme(),
        typography = visualAgentTypography(DEFAULT_STARTUP_FONT_SIZE),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = @Suppress("DEPRECATION") painterResource("icons/visual-agent.png"),
                    contentDescription = AppIdentity.DISPLAY_NAME,
                    modifier = Modifier.padding(bottom = 28.dp).size(STARTUP_ICON_SIZE),
                )
                Text(text = AppIdentity.DISPLAY_NAME, style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = status.message(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp),
                )
                if (status.phase == StartupPhase.FAILED) {
                    Button(onClick = onRetry, modifier = Modifier.padding(top = 28.dp)) { Text("Retry") }
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(top = 32.dp).size(STARTUP_PROGRESS_SIZE),
                        strokeWidth = 3.dp,
                    )
                }
            }
        }
    }
}

private const val DEFAULT_STARTUP_FONT_SIZE = 14
private val DEFAULT_SPLASH_WIDTH = 880.dp
private val DEFAULT_SPLASH_HEIGHT = 600.dp
private val STARTUP_ICON_SIZE = 180.dp
private val STARTUP_PROGRESS_SIZE = 44.dp
private val DEFAULT_MAIN_WINDOW_WIDTH = 1280.dp
private val DEFAULT_MAIN_WINDOW_HEIGHT = 820.dp
private const val STARTUP_WINDOW_TITLE = "Visual Agent"
