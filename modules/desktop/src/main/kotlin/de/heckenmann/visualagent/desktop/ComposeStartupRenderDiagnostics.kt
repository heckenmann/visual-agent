package de.heckenmann.visualagent.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext

/** Creates a native startup window with the shared local diagnostics and optional visual probe. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun StartupWindow(
    mode: StartupWindowMode,
    onCloseRequest: () -> Unit,
    title: String,
    icon: Painter,
    state: WindowState,
    visible: Boolean = true,
    decoration: WindowDecoration = WindowDecoration.SystemDefault,
    resizable: Boolean = true,
    observeStartup: Boolean = true,
    content: @Composable FrameWindowScope.() -> Unit,
) {
    val launchWindow: @Composable () -> Unit = {
        Window(
            onCloseRequest = onCloseRequest,
            title = title,
            icon = icon,
            state = state,
            visible = visible,
            decoration = decoration,
            resizable = resizable,
        ) {
            val showPattern = remember { System.getenv("VISUAL_AGENT_STARTUP_RENDER_PATTERN") == "1" }
            if (observeStartup && showPattern) {
                StartupRenderPattern { content() }
            } else {
                content()
            }
        }
    }
    if (observeStartup) ObserveStartupRendering(mode, launchWindow) else launchWindow()
}

// Window reads this CompositionLocal before installing its content or drawing its first frame.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ObserveStartupRendering(
    mode: StartupWindowMode,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val renderLog = LocalStartupRenderLog.current
    val diagnostics =
        remember(mode, renderLog) {
            StartupRenderDiagnostics(mode) { event -> renderLog.report("Desktop startup rendering: $event") }
        }
    val parentFactory = LocalWindowExceptionHandlerFactory.current
    val monitoringFactory =
        remember(parentFactory, diagnostics) {
            var observedWindow: ComposeWindow? = null
            WindowExceptionHandlerFactory { window ->
                val delegate = parentFactory.exceptionHandler(window)
                if (observedWindow == null && window is ComposeWindow) {
                    observedWindow = window
                    window.onRenderApiChanged { diagnostics.backendChanged(window.renderApi.name) }
                    diagnostics.backendChanged(window.renderApi.name)
                }
                if (window !== observedWindow) {
                    delegate
                } else {
                    WindowExceptionHandler { failure ->
                        diagnostics.exceptionReported(failure)
                        delegate.onException(failure)
                    }
                }
            }
        }
    DisposableEffect(diagnostics) {
        renderLog.window = mode
        val uiThread = Thread.currentThread()
        val delegate = uiThread.uncaughtExceptionHandler
        val handler =
            Thread.UncaughtExceptionHandler { thread, failure ->
                renderLog.nativeFailure(failure)
                delegate.uncaughtException(thread, failure)
            }
        uiThread.uncaughtExceptionHandler = handler
        diagnostics.start()
        val watchdog =
            scope.launch(Dispatchers.Default) {
                diagnostics.watch(dispatchToUi = { heartbeat -> Dispatchers.Main.dispatch(EmptyCoroutineContext, heartbeat) })
            }
        onDispose {
            if (uiThread.uncaughtExceptionHandler === handler) uiThread.uncaughtExceptionHandler = delegate
            diagnostics.stop()
            watchdog.cancel()
        }
    }
    CompositionLocalProvider(LocalWindowExceptionHandlerFactory provides monitoringFactory, content = content)
}
