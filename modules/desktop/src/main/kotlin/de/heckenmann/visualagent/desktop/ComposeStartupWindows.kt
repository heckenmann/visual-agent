package de.heckenmann.visualagent.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import de.heckenmann.visualagent.desktop.generated.resources.Res
import de.heckenmann.visualagent.desktop.generated.resources.visual_agent
import de.heckenmann.visualagent.protocol.ApplicationPort
import de.heckenmann.visualagent.protocol.LayoutSize
import de.heckenmann.visualagent.protocol.LayoutWindowState
import de.heckenmann.visualagent.protocol.OnboardingState
import de.heckenmann.visualagent.protocol.OnboardingStatus
import de.heckenmann.visualagent.protocol.ThemeMode
import de.heckenmann.visualagent.protocol.WorkspaceLayoutSnapshot
import de.heckenmann.visualagent.ui.application.ComposeApplicationDependencies
import de.heckenmann.visualagent.ui.application.StartupPhase
import de.heckenmann.visualagent.ui.application.StartupStatus
import de.heckenmann.visualagent.ui.application.VisualAgentComposeApp
import de.heckenmann.visualagent.ui.application.WorkspaceLayoutPersistenceCoordinator
import de.heckenmann.visualagent.ui.onboarding.ComposeOnboardingWizard
import de.heckenmann.visualagent.ui.workspace.visualAgentDarkColorScheme
import de.heckenmann.visualagent.ui.workspace.visualAgentLightColorScheme
import de.heckenmann.visualagent.ui.workspace.visualAgentTypography
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.slf4j.LoggerFactory

/** Identifies which native window the desktop host must currently render. */
internal enum class StartupWindowMode {
    /** The server is still starting or startup failed. */
    SPLASH,

    /** The server is ready and the persisted workspace can be shown. */
    MAIN,

    /** The connected server requires provider/model setup before the workspace may open. */
    ONBOARDING,
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
    onboarding: OnboardingState? = null,
    manualOnboardingRequested: Boolean = false,
    skipAutomaticOnboarding: Boolean = false,
): StartupWindowMode =
    when {
        status.phase != StartupPhase.READY || dependencies == null -> StartupWindowMode.SPLASH
        manualOnboardingRequested || (!skipAutomaticOnboarding && onboarding?.status == OnboardingStatus.NOT_STARTED) ->
            StartupWindowMode.ONBOARDING
        else -> StartupWindowMode.MAIN
    }

/** Renders the independent, centered, frameless startup window. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ComposeStartupSplashWindow(
    status: StartupStatus,
    bookmarks: DesktopServerBookmarkLoadResult = DesktopServerBookmarkLoadResult.Loaded(DesktopServerBookmarkState()),
    onSaveBookmarks: (DesktopServerBookmarkState) -> Unit = {},
    onStartLocal: () -> Unit = {},
    onRetry: () -> Unit,
    onCloseRequest: () -> Unit,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
    val applicationIcon = painterResource(Res.drawable.visual_agent)
    val windowState =
        rememberWindowState(
            width = DEFAULT_SPLASH_WIDTH,
            height = DEFAULT_SPLASH_HEIGHT,
            position = WindowPosition.Aligned(Alignment.Center),
        )
    Window(
        onCloseRequest = onCloseRequest,
        title = "$STARTUP_WINDOW_TITLE – Starting",
        icon = applicationIcon,
        state = windowState,
        decoration = WindowDecoration.Undecorated(),
        resizable = false,
    ) {
        ComposeStartupSplash(
            status = status,
            bookmarks = bookmarks,
            onSaveBookmarks = onSaveBookmarks,
            onStartLocal = onStartLocal,
            onRetry = onRetry,
            themeMode = themeMode,
            onCloseRequest = onCloseRequest,
        )
    }
}

/** Renders the explicit provider/model onboarding phase for the connected Visual Agent server. */
@Composable
internal fun ComposeOnboardingWindow(
    applicationPort: ApplicationPort,
    automatic: Boolean,
    onFinished: () -> Unit,
    onCloseRequest: () -> Unit,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
    val applicationIcon = painterResource(Res.drawable.visual_agent)
    var skipRequested by remember { mutableStateOf(false) }
    Window(
        onCloseRequest = {
            if (automatic) skipRequested = true else onCloseRequest()
        },
        title = "$STARTUP_WINDOW_TITLE – Setup",
        icon = applicationIcon,
        resizable = false,
        state =
            rememberWindowState(
                width = DEFAULT_SPLASH_WIDTH,
                height = DEFAULT_SPLASH_HEIGHT,
                position = WindowPosition.Aligned(Alignment.Center),
            ),
    ) {
        val darkTheme = startupDarkTheme(themeMode)
        MaterialTheme(
            colorScheme = if (darkTheme) visualAgentDarkColorScheme() else visualAgentLightColorScheme(),
            typography = visualAgentTypography(DEFAULT_STARTUP_FONT_SIZE),
        ) {
            ComposeOnboardingBackground(darkTheme) {
                ComposeOnboardingWizard(
                    onboarding = applicationPort.onboarding,
                    automatic = automatic,
                    onFinished = onFinished,
                    skipRequested = skipRequested,
                    onSkipRequestHandled = { skipRequested = false },
                )
            }
        }
    }
}

/** Renders the main window with its own state and independent lifecycle. */
@Composable
internal fun ComposeMainWindow(
    dependencies: ComposeApplicationDependencies,
    persistedLayout: WorkspaceLayoutSnapshot,
    persistedWindows: List<LayoutWindowState>,
    onCloseApplication: suspend (WindowState, WorkspaceLayoutPersistenceCoordinator) -> Unit,
    onRunOnboarding: () -> Unit,
) {
    val applicationIcon = painterResource(Res.drawable.visual_agent)
    val scope = rememberCoroutineScope()
    val layoutPersistence =
        remember(dependencies.applicationPort.layout) {
            WorkspaceLayoutPersistenceCoordinator(dependencies.applicationPort.layout)
        }
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
    val requestClose: () -> Unit = { scope.launch { onCloseApplication(windowState, layoutPersistence) } }
    Window(
        visible = geometryRestored,
        onCloseRequest = requestClose,
        title = STARTUP_WINDOW_TITLE,
        icon = applicationIcon,
        state = windowState,
    ) {
        LaunchedEffect(persistedLayout) {
            restoreMainWindowGeometry(windowState, persistedLayout, currentScreenBounds())
            geometryRestored = true
        }
        LaunchedEffect(geometryRestored) {
            if (geometryRestored &&
                DesktopStartupSmokeSupport.autoStartRequested(System.getProperty(DesktopStartupSmokeSupport.AUTO_START_PROPERTY))
            ) {
                LoggerFactory
                    .getLogger("de.heckenmann.visualagent.desktop.ComposeStartupHost")
                    .info(DesktopStartupSmokeSupport.READY_LOG_MARKER)
            }
        }
        VisualAgentComposeApp(
            deps = dependencies,
            onCloseApplication = requestClose,
            persistedWindows = persistedWindows,
            onRunOnboarding = onRunOnboarding,
            layoutPersistence = layoutPersistence,
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

/** Applies the startup theme and renders the server-selection splash surface. */
@Composable
internal fun ComposeStartupSplash(
    status: StartupStatus,
    bookmarks: DesktopServerBookmarkLoadResult = DesktopServerBookmarkLoadResult.Loaded(DesktopServerBookmarkState()),
    onSaveBookmarks: (DesktopServerBookmarkState) -> Unit = {},
    onStartLocal: () -> Unit = {},
    onRetry: () -> Unit,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onCloseRequest: () -> Unit = {},
) {
    val bookmarksState = (bookmarks as? DesktopServerBookmarkLoadResult.Loaded)?.state ?: DesktopServerBookmarkState()
    var bookmarkDialog by remember { mutableStateOf<StartupServerBookmarkDialog?>(null) }
    val darkTheme = startupDarkTheme(themeMode)
    MaterialTheme(
        colorScheme = if (darkTheme) visualAgentDarkColorScheme() else visualAgentLightColorScheme(),
        typography = visualAgentTypography(DEFAULT_STARTUP_FONT_SIZE),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ComposeStartupSplashPresentation(
                status = status,
                bookmarks = bookmarks,
                onStartLocal = onStartLocal,
                onRetry = onRetry,
                onCreateServer = { bookmarkDialog = StartupServerBookmarkDialog.Create },
                onEditServer = { bookmarkDialog = StartupServerBookmarkDialog.Edit(it) },
                darkTheme = darkTheme,
                onCloseRequest = onCloseRequest,
            )
            ComposeStartupServerBookmarkDialog(
                dialog = bookmarkDialog,
                state = bookmarksState,
                onSave = onSaveBookmarks,
                onDismiss = { bookmarkDialog = null },
                onDialogChange = { bookmarkDialog = it },
            )
        }
    }
}

private const val DEFAULT_STARTUP_FONT_SIZE = 14
private val DEFAULT_SPLASH_WIDTH = 880.dp
private val DEFAULT_SPLASH_HEIGHT = 600.dp
private val DEFAULT_MAIN_WINDOW_WIDTH = 1280.dp
private val DEFAULT_MAIN_WINDOW_HEIGHT = 820.dp
private const val STARTUP_WINDOW_TITLE = "Visual Agent"
