package de.heckenmann.visualagent.ui

import javafx.application.Platform
import javafx.geometry.Rectangle2D
import javafx.stage.Screen
import javafx.stage.Stage
import kotlinx.serialization.Serializable
import org.springframework.stereotype.Service
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Provides live workspace window state to UI code and model tools.
 */
@Service
class WorkspaceLayoutService(
    private val persistence: WorkspaceLayoutPersistence,
) {
    @Volatile
    private var manager: WorkspaceWindowManager? = null

    @Volatile
    private var stage: Stage? = null

    /** Registers the live JavaFX workspace manager for runtime layout reads and writes. */
    internal fun bind(
        manager: WorkspaceWindowManager,
        stage: Stage,
    ) {
        this.manager = manager
        this.stage = stage
    }

    /** Returns the live layout report, or persisted window state when the UI is not active. */
    fun report(): WorkspaceLayoutReport =
        runOnFxThreadOrNull {
            val currentManager = manager
            val currentStage = stage
            WorkspaceLayoutReport(
                stage =
                    currentStage?.let {
                        StageState(it.width, it.height)
                    },
                desktop =
                    currentManager?.desktopSize()?.let {
                        DesktopState(it.first, it.second)
                    },
                screens = Screen.getScreens().mapIndexed { index, screen -> screen.toState(index) },
                windows = currentManager?.snapshot()?.windows ?: persistence.load().windows,
            )
        } ?: WorkspaceLayoutReport(windows = persistence.load().windows)

    /** Applies window states to the live workspace when present and persists the requested layout. */
    fun applyWindowStates(states: List<WorkspaceWindowState>): WorkspaceLayout {
        val layout = WorkspaceLayout(states)
        runOnFxThreadOrNull {
            manager?.restore(layout)
        }
        persistence.save(layout)
        return layout
    }

    private fun Screen.toState(index: Int): ScreenState =
        ScreenState(
            index = index,
            bounds = bounds.toSerializable(),
            visualBounds = visualBounds.toSerializable(),
            dpi = dpi,
        )

    private fun Rectangle2D.toSerializable(): BoundsState =
        BoundsState(
            minX = minX,
            minY = minY,
            width = width,
            height = height,
        )

    private fun <T> runOnFxThreadOrNull(action: () -> T): T? =
        runCatching {
            if (Platform.isFxApplicationThread()) {
                action()
            } else {
                val latch = CountDownLatch(1)
                val result = AtomicReference<Result<T>>()
                Platform.runLater {
                    result.set(runCatching(action))
                    latch.countDown()
                }
                if (!latch.await(5, TimeUnit.SECONDS)) return null
                result.get().getOrNull()
            }
        }.getOrNull()
}

/**
 * Snapshot of live or persisted workspace geometry exposed to model tools.
 */
@Serializable
data class WorkspaceLayoutReport(
    /** Main JavaFX stage dimensions, if a live stage is available. */
    val stage: StageState? = null,
    /** Internal desktop dimensions, if the live UI is available. */
    val desktop: DesktopState? = null,
    /** Available display screens and their usable bounds. */
    val screens: List<ScreenState> = emptyList(),
    /** Internal workspace window states. */
    val windows: List<WorkspaceWindowState> = emptyList(),
)

/**
 * Main JavaFX window size.
 */
@Serializable
data class StageState(
    /** Stage width in pixels. */
    val width: Double,
    /** Stage height in pixels. */
    val height: Double,
)

/**
 * Internal workspace desktop size.
 */
@Serializable
data class DesktopState(
    /** Desktop width in pixels. */
    val width: Double,
    /** Desktop height in pixels. */
    val height: Double,
)

/**
 * Display screen geometry available to the JavaFX runtime.
 */
@Serializable
data class ScreenState(
    /** Stable index within the current JavaFX screen list. */
    val index: Int,
    /** Full screen bounds. */
    val bounds: BoundsState,
    /** Usable screen bounds excluding OS UI where reported. */
    val visualBounds: BoundsState,
    /** Screen DPI reported by JavaFX. */
    val dpi: Double,
)

/**
 * Serializable rectangle bounds.
 */
@Serializable
data class BoundsState(
    /** Minimum x-coordinate. */
    val minX: Double,
    /** Minimum y-coordinate. */
    val minY: Double,
    /** Width in pixels. */
    val width: Double,
    /** Height in pixels. */
    val height: Double,
)
