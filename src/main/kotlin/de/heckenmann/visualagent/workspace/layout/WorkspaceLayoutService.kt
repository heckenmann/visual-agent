package de.heckenmann.visualagent.workspace.layout

import kotlinx.serialization.Serializable
import org.springframework.stereotype.Service
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Provides live or persisted workspace window state to UI code and model tools.
 */
@Service
class WorkspaceLayoutService(
    private val persistence: WorkspaceLayoutPersistence,
) {
    @Volatile
    private var stage: StageState? = null

    @Volatile
    private var desktop: DesktopState? = null

    @Volatile
    private var liveWindows: List<WorkspaceWindowState>? = null

    private val listeners = CopyOnWriteArrayList<(List<WorkspaceWindowState>) -> Unit>()

    /** Registers the live Compose workspace geometry for runtime layout reads. */
    fun bind(
        stage: StageState,
        desktop: DesktopState,
        windows: List<WorkspaceWindowState>,
    ) {
        this.stage = stage
        this.desktop = desktop
        this.liveWindows = windows
    }

    /** Returns the live layout report, or persisted window state when the UI is not active. */
    fun report(): WorkspaceLayoutReport =
        WorkspaceLayoutReport(
            stage = stage,
            desktop = desktop,
            screens = emptyList(),
            windows = liveWindows ?: persistence.load().windows,
        )

    /**
     * Applies window states to the live workspace and persists the requested layout.
     *
     * @param states Complete workspace window state list
     * @param notifyListeners Whether registered live UI listeners should be notified
     * @return Persisted workspace layout
     */
    fun applyWindowStates(
        states: List<WorkspaceWindowState>,
        notifyListeners: Boolean = true,
    ): WorkspaceLayout {
        val layout = WorkspaceLayout(states)
        liveWindows = states
        persistence.save(layout)
        if (notifyListeners) {
            listeners.forEach { listener -> listener(states) }
        }
        return layout
    }

    /**
     * Registers a listener for workspace state changes applied by UI code or model tools.
     *
     * @param listener Callback receiving the complete current workspace window state list
     * @return Handle that unregisters the listener when closed
     */
    fun addWindowStateListener(listener: (List<WorkspaceWindowState>) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }
}

/**
 * Snapshot of live or persisted workspace geometry exposed to model tools.
 */
@Serializable
data class WorkspaceLayoutReport(
    /** Main window dimensions, if a live window is available. */
    val stage: StageState? = null,
    /** Internal desktop dimensions, if the live UI is available. */
    val desktop: DesktopState? = null,
    /** Available display screens and their usable bounds. */
    val screens: List<ScreenState> = emptyList(),
    /** Internal workspace window states. */
    val windows: List<WorkspaceWindowState> = emptyList(),
)

/**
 * Main window size.
 */
@Serializable
data class StageState(
    /** Window width in pixels. */
    val width: Double,
    /** Window height in pixels. */
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
 * Display screen geometry available to the runtime.
 */
@Serializable
data class ScreenState(
    /** Stable index within the current screen list. */
    val index: Int,
    /** Full screen bounds. */
    val bounds: BoundsState,
    /** Usable screen bounds excluding OS UI where reported. */
    val visualBounds: BoundsState,
    /** Screen DPI reported by the runtime. */
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
