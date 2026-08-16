@file:Suppress("FunctionName")

package de.heckenmann.visualagent.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.heckenmann.visualagent.AppIdentity
import de.heckenmann.visualagent.VisualAgentApplication
import de.heckenmann.visualagent.protocol.ApplicationConnection
import de.heckenmann.visualagent.protocol.ApplicationPort
import de.heckenmann.visualagent.protocol.LayoutPosition
import de.heckenmann.visualagent.protocol.LayoutSize
import de.heckenmann.visualagent.protocol.LayoutWindowState
import de.heckenmann.visualagent.protocol.WorkspaceLayoutSnapshot
import de.heckenmann.visualagent.server.VisualAgentGrpcServer
import de.heckenmann.visualagent.ui.application.ComposeApplicationDependencies
import de.heckenmann.visualagent.ui.application.StartupPhase
import de.heckenmann.visualagent.ui.application.StartupStatus
import de.heckenmann.visualagent.ui.application.VisualAgentComposeApp
import de.heckenmann.visualagent.ui.workspace.visualAgentDarkColorScheme
import de.heckenmann.visualagent.ui.workspace.visualAgentTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext

/** Starts Compose before the local Spring server and keeps the splash responsive during startup. */
fun runVisualAgentComposeApplication() {
    AppIdentity.configureProcessProperties()
    application { ComposeStartupHost(::exitApplication) }
}

@Composable
private fun ComposeStartupHost(exitApplication: () -> Unit) {
    var startupAttempt by remember { mutableStateOf(0) }
    var startupStatus by remember { mutableStateOf(StartupStatus.initial()) }
    var springContext by remember { mutableStateOf<ConfigurableApplicationContext?>(null) }
    var serverConnection by remember { mutableStateOf<ApplicationConnection?>(null) }
    var dependencies by remember { mutableStateOf<ComposeApplicationDependencies?>(null) }
    var persistedLayout by remember { mutableStateOf<WorkspaceLayoutSnapshot?>(null) }
    var persistedWindows by remember { mutableStateOf<List<LayoutWindowState>>(emptyList()) }
    val currentContext by rememberUpdatedState(springContext)
    val windowState =
        rememberWindowState(
            width = DEFAULT_WINDOW_WIDTH,
            height = DEFAULT_WINDOW_HEIGHT,
            position = WindowPosition.Aligned(Alignment.Center),
        )

    LaunchedEffect(startupAttempt) {
        startupStatus = StartupStatus.resolvingEndpoint()
        dependencies = null
        persistedLayout = null
        serverConnection?.close()
        serverConnection = null
        springContext = null
        var endpoint: DesktopServerEndpoint? = null
        try {
            endpoint = withContext(Dispatchers.IO) { selectEndpoint() }
            val selectedEndpoint = endpoint
            if (selectedEndpoint is DesktopServerEndpoint.RemoteTls) {
                startupStatus = StartupStatus.connectingRemote()
                startupStatus = StartupStatus.handshaking()
                withContext(Dispatchers.IO) { awaitProtocolHandshake(selectedEndpoint) }
                error("Remote application transport is not available in this desktop build")
            }
            val localEndpoint =
                selectedEndpoint as? DesktopServerEndpoint.LocalInProcess
                    ?: error("Unsupported desktop server endpoint")
            startupStatus = StartupStatus.startingServer()
            val context =
                runInterruptible(Dispatchers.IO) {
                    SpringApplicationBuilder(VisualAgentApplication::class.java)
                        .web(WebApplicationType.NONE)
                        .properties("visualagent.server.in-process-name=${localEndpoint.name}")
                        .run()
                }
            springContext = context
            startupStatus = StartupStatus.loadingRuntime()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            startupStatus =
                if (endpoint is DesktopServerEndpoint.RemoteTls) {
                    StartupStatus.failed("The remote server could not be used by this desktop build")
                } else {
                    StartupStatus.failed()
                }
        }
    }

    LaunchedEffect(springContext) {
        val context = springContext ?: return@LaunchedEffect
        try {
            val localServer =
                withContext(Dispatchers.IO) {
                    context.getBean(VisualAgentGrpcServer::class.java)
                }
            val connection =
                LocalApplicationConnection(localServer) {
                    context.getBean(ApplicationPort::class.java)
                }
            withContext(Dispatchers.IO) { connection.awaitReady() }
            serverConnection = connection
            startupStatus = StartupStatus.loadingRuntime()
            val applicationPort = withContext(Dispatchers.IO) { connection.application }
            val loadedDependencies =
                ComposeApplicationDependencies(
                    applicationPort = applicationPort,
                    beanDefinitionCount = context.beanDefinitionCount,
                    clientImagePort = LocalClientImagePort(),
                )
            val loadedLayout = withContext(Dispatchers.IO) { applicationPort.layout.report() }
            dependencies = loadedDependencies
            persistedLayout = loadedLayout
            persistedWindows = loadedLayout.windows
            startupStatus = StartupStatus.ready()
        } catch (_: Exception) {
            serverConnection?.close()
            context.close()
            springContext = null
            startupStatus = StartupStatus.failed()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            serverConnection?.close()
            currentContext?.close()
        }
    }

    val readyDependencies = dependencies
    Window(
        onCloseRequest = {
            if (readyDependencies != null) {
                closeApplication(readyDependencies, windowState, exitApplication)
            } else {
                serverConnection?.close()
                currentContext?.close()
                exitApplication()
            }
        },
        title = AppIdentity.DISPLAY_NAME,
        icon = @Suppress("DEPRECATION") painterResource("icons/visual-agent.png"),
        state = windowState,
    ) {
        LaunchedEffect(persistedLayout, startupStatus.phase) {
            if (startupStatus.phase != StartupPhase.READY) return@LaunchedEffect
            val layout = persistedLayout ?: return@LaunchedEffect
            layout.stage?.let { stage -> windowState.size = DpSize(stage.width.dp, stage.height.dp) }
            layout.stagePosition?.let { position ->
                val size = windowState.size
                val restored =
                    currentScreenBounds()?.let { bounds ->
                        clampWindowPosition(
                            position,
                            LayoutSize(size.width.value.toDouble(), size.height.value.toDouble()),
                            bounds,
                        )
                    } ?: position
                windowState.position = WindowPosition.Absolute(restored.x.dp, restored.y.dp)
            }
        }
        if (readyDependencies == null || startupStatus.phase != StartupPhase.READY) {
            ComposeStartupSplash(status = startupStatus, onRetry = { startupAttempt += 1 })
        } else {
            VisualAgentComposeApp(
                deps = readyDependencies,
                onCloseApplication = {
                    closeApplication(readyDependencies, windowState, exitApplication)
                },
                persistedWindows = persistedWindows,
            )
        }
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

/** Keeps a restored window position within the usable bounds of its current screen. */
internal fun clampWindowPosition(
    position: LayoutPosition,
    windowSize: LayoutSize,
    screenBounds: ScreenBounds,
): LayoutPosition {
    val maxX = (screenBounds.x + screenBounds.width - windowSize.width).coerceAtLeast(screenBounds.x)
    val maxY = (screenBounds.y + screenBounds.height - windowSize.height).coerceAtLeast(screenBounds.y)
    return LayoutPosition(
        x = position.x.coerceIn(screenBounds.x, maxX),
        y = position.y.coerceIn(screenBounds.y, maxY),
    )
}

/** Usable screen bounds in Compose desktop density-independent coordinates. */
data class ScreenBounds(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
)

internal fun selectEndpoint(): DesktopServerEndpoint {
    val properties =
        System.getProperties().stringPropertyNames().associateWith { key ->
            System.getProperty(key).orEmpty()
        }
    return DesktopServerEndpointSelector.select(properties)
}

internal fun closeApplication(
    dependencies: ComposeApplicationDependencies,
    windowState: androidx.compose.ui.window.WindowState,
    exitApplication: () -> Unit,
) {
    dependencies.applicationPort.lifecycle.beginShutdown()
    try {
        dependencies.applicationPort.cancelActiveWork()
        val size = windowState.size
        val position =
            windowState.position.takeIf { it.isSpecified }?.let {
                LayoutPosition(it.x.value.toDouble(), it.y.value.toDouble())
            }
        dependencies.applicationPort.layout.saveStage(
            LayoutSize(size.width.value.toDouble(), size.height.value.toDouble()),
            position,
        )
    } finally {
        // Exit the Compose application before the Spring context is closed. The root
        // DisposableEffect cancels presentation coroutines and unregisters their listeners
        // before it closes the context. Closing Spring here races with a final TodoPanel
        // refresh and can leave it trying to create an EntityManager from a closed factory.
        exitApplication()
    }
}

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
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = @Suppress("DEPRECATION") painterResource("icons/visual-agent.png"),
                    contentDescription = AppIdentity.DISPLAY_NAME,
                    modifier = Modifier.padding(bottom = 20.dp),
                )
                Text(text = AppIdentity.DISPLAY_NAME, style = MaterialTheme.typography.headlineMedium)
                Text(text = status.message(), modifier = Modifier.padding(top = 12.dp))
                if (status.phase == StartupPhase.FAILED) {
                    Button(onClick = onRetry, modifier = Modifier.padding(top = 20.dp)) { Text("Retry") }
                } else {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 20.dp))
                }
            }
        }
    }
}

private const val DEFAULT_STARTUP_FONT_SIZE = 14
private val DEFAULT_WINDOW_WIDTH = 1280.dp
private val DEFAULT_WINDOW_HEIGHT = 820.dp
