package de.heckenmann.visualagent.desktop

import androidx.compose.runtime.staticCompositionLocalOf
import org.slf4j.LoggerFactory
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

internal val LocalStartupRenderLog = staticCompositionLocalOf<StartupRenderLog> { error("Startup render log is unavailable") }

/** Writes startup events in the background so a slow log destination cannot stall the UI. */
internal class StartupRenderLog : AutoCloseable {
    private val logger = LoggerFactory.getLogger("de.heckenmann.visualagent.desktop.StartupRendering")
    private val events = ArrayBlockingQueue<String>(128)

    @Volatile
    private var closed = false

    /** Latest observed window, retained for exceptions escaping application composition. */
    @Volatile
    var window: StartupWindowMode? = null

    private val writer =
        Thread.ofPlatform().daemon().name("startup-render-log").start {
            while (!closed || events.isNotEmpty()) {
                val event = events.poll(100, TimeUnit.MILLISECONDS) ?: continue
                if ("error cause=" in event || "ui-unresponsive" in event) {
                    logger.warn(event)
                } else {
                    logger.info(event)
                }
            }
        }

    /** Enqueues an event without waiting for the log destination. */
    fun report(event: String) {
        events.offer(event)
    }

    /** Reports native Skiko failures that escape the window's Compose exception handler. */
    fun nativeFailure(failure: Throwable) {
        val mode = window ?: return
        val renderFailure = nativeStartupRenderFailure(failure) ?: return
        report("Desktop startup rendering: window=$mode render-error cause=${renderFailure.javaClass.name}")
    }

    override fun close() {
        closed = true
        writer.join(1_000)
    }
}

/** Finds a Skiko render exception originating in the native backend initialization path. */
internal fun nativeStartupRenderFailure(failure: Throwable): Throwable? {
    val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
    var cause: Throwable? = failure
    while (cause != null && visited.add(cause)) {
        if (cause.javaClass.name == "org.jetbrains.skiko.RenderException" &&
            cause.stackTrace.any { it.className == "org.jetbrains.skiko.redrawer.RedrawerManager" }
        ) {
            return cause
        }
        cause = cause.cause
    }
    return null
}
