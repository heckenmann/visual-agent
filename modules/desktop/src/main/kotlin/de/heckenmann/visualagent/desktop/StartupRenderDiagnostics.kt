package de.heckenmann.visualagent.desktop

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Watches one startup window independently of server and compositor readiness. */
internal class StartupRenderDiagnostics(
    private val mode: StartupWindowMode,
    private val report: (String) -> Unit,
) {
    private val active = AtomicBoolean(false)
    private var backend: String? = null

    /** Marks the window as under observation before Compose creates its native peer. */
    fun start() {
        active.set(true)
        emit("observing visualIntegrity=unknown")
    }

    /** Stops accepting events when the window is replaced or disposed. */
    fun stop() {
        active.set(false)
    }

    /** Records the initial backend and each subsequent change reported by Compose. */
    fun backendChanged(api: String) {
        val previous = backend
        if (api == previous) return
        backend = api
        emit("backend=$api previousBackend=${previous ?: "unknown"}")
    }

    /** Receives uncaught Compose window exceptions without changing how Compose handles them. */
    fun exceptionReported(failure: Throwable) {
        // Compose delivers render, event-handler, and background exceptions through the same hook.
        val event =
            if (failure.javaClass.name == "org.jetbrains.skiko.RenderException" ||
                failure.stackTrace.any { it.className == "androidx.compose.ui.scene.ComposeSceneMediator" && it.methodName == "onRender" }
            ) {
                "render-error"
            } else {
                "window-error"
            }
        emit("$event cause=${failure.javaClass.name}")
    }

    /** Checks that queued work reaches the UI dispatcher, even while server startup is pending. */
    suspend fun watch(
        dispatchToUi: (Runnable) -> Unit,
        timeout: Duration = 5.seconds,
        interval: Duration = 1.seconds,
    ) {
        if (mode == StartupWindowMode.MAIN) {
            try {
                withTimeoutOrNull(15.seconds) { watchUntilStopped(dispatchToUi, timeout, interval) }
            } finally {
                stop()
            }
        } else {
            watchUntilStopped(dispatchToUi, timeout, interval)
        }
    }

    private suspend fun watchUntilStopped(
        dispatchToUi: (Runnable) -> Unit,
        timeout: Duration,
        interval: Duration,
    ) {
        while (active.get()) {
            val heartbeat = CompletableDeferred<Unit>()
            dispatchToUi(Runnable { heartbeat.complete(Unit) })
            try {
                if (withTimeoutOrNull(timeout) { heartbeat.await() } == null) {
                    emit("ui-unresponsive timeoutMs=${timeout.inWholeMilliseconds}")
                    // Keep a single queued callback during a stall. Begin sampling again after it runs.
                    heartbeat.await()
                    emit("ui-responsive")
                }
            } finally {
                heartbeat.cancel()
            }
            delay(interval)
        }
    }

    private fun emit(event: String) {
        if (active.get()) report("window=$mode $event")
    }
}
