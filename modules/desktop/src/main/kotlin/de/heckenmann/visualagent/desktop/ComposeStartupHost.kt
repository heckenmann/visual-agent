@file:Suppress("FunctionName")

package de.heckenmann.visualagent.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.application
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
import de.heckenmann.visualagent.ui.application.StartupStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
    val currentContext = rememberUpdatedState(springContext)
    val currentConnection = rememberUpdatedState(serverConnection)
    val shutdownCoordinator = remember { DesktopShutdownCoordinator() }

    LaunchedEffect(startupAttempt) {
        startupStatus = StartupStatus.resolvingEndpoint()
        dependencies = null
        persistedLayout = null
        val previousConnection = serverConnection
        serverConnection = null
        val previousContext = springContext
        springContext = null
        withContext(Dispatchers.IO) {
            previousConnection?.close()
            previousContext?.close()
        }
        var endpoint: DesktopServerEndpoint? = null
        var context: ConfigurableApplicationContext? = null
        var contextPublished = false
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
            context =
                runInterruptible(Dispatchers.IO) {
                    SpringApplicationBuilder(VisualAgentApplication::class.java)
                        .web(WebApplicationType.NONE)
                        .properties("visualagent.server.in-process-name=${localEndpoint.name}")
                        .run()
                }
            springContext = context
            contextPublished = true
            startupStatus = StartupStatus.loadingRuntime()
        } catch (cancelled: CancellationException) {
            if (!contextPublished) {
                withContext(Dispatchers.IO + NonCancellable) { context?.close() }
            }
            throw cancelled
        } catch (_: Exception) {
            withContext(Dispatchers.IO + NonCancellable) { context?.close() }
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
        var connection: ApplicationConnection? = null
        try {
            val localServer =
                withContext(Dispatchers.IO) {
                    context.getBean(VisualAgentGrpcServer::class.java)
                }
            connection =
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            serverConnection = null
            springContext = null
            withContext(Dispatchers.IO + NonCancellable) {
                connection?.close()
                context.close()
            }
            startupStatus = StartupStatus.failed()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            shutdownCoordinator.closeResources {
                currentConnection.value?.close()
                currentContext.value?.close()
            }
        }
    }

    val readyDependencies = dependencies
    if (startupWindowMode(startupStatus, readyDependencies) == StartupWindowMode.SPLASH) {
        ComposeStartupSplashWindow(
            status = startupStatus,
            onRetry = { startupAttempt += 1 },
            onCloseRequest = {
                if (shutdownCoordinator.requestExit()) {
                    exitApplication()
                }
            },
        )
    } else {
        ComposeMainWindow(
            dependencies = checkNotNull(readyDependencies),
            persistedLayout = checkNotNull(persistedLayout),
            persistedWindows = persistedWindows,
            onCloseApplication = { windowState ->
                closeApplication(
                    dependencies = checkNotNull(readyDependencies),
                    windowState = windowState,
                    exitApplication = exitApplication,
                    shutdownCoordinator = shutdownCoordinator,
                )
            },
        )
    }
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
    shutdownCoordinator: DesktopShutdownCoordinator = DesktopShutdownCoordinator(),
) {
    if (!shutdownCoordinator.requestExit()) return
    try {
        dependencies.applicationPort.lifecycle.beginShutdown()
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
    } catch (_: Exception) {
        // A shutdown failure must not keep the native window or the Compose application alive.
    } finally {
        // Exit the Compose application before the Spring context is closed. The root
        // DisposableEffect cancels presentation coroutines and unregisters their listeners
        // before it closes the context. Closing Spring here races with a final TodoPanel
        // refresh and can leave it trying to create an EntityManager from a closed factory.
        exitApplication()
    }
}
