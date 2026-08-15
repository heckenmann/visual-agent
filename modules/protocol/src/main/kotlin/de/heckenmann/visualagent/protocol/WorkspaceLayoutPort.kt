package de.heckenmann.visualagent.protocol

/** Workspace layout operations exposed to the desktop presentation client. */
interface WorkspaceLayoutPort {
    /** Reads persisted or currently live window state. */
    fun report(): WorkspaceLayoutSnapshot

    /** Binds the current desktop geometry to the application runtime. */
    fun bind(
        stage: LayoutSize,
        desktop: LayoutSize,
        windows: List<LayoutWindowState>,
    )

    /** Persists the supplied window state and optionally notifies listeners. */
    fun applyWindowStates(
        states: List<LayoutWindowState>,
        notifyListeners: Boolean = true,
    )

    /** Persists the main window size. */
    fun saveStage(
        stage: LayoutSize,
        position: LayoutPosition? = null,
    )

    /** Registers for layout changes initiated by another client or the server. */
    fun addWindowStateListener(listener: (List<LayoutWindowState>) -> Unit): AutoCloseable
}

/** Current workspace layout snapshot. */
data class WorkspaceLayoutSnapshot(
    val stage: LayoutSize? = null,
    val stagePosition: LayoutPosition? = null,
    val desktop: LayoutSize? = null,
    val windows: List<LayoutWindowState> = emptyList(),
)

/** Two-dimensional layout size in device-independent transport units. */
data class LayoutSize(
    val width: Double,
    val height: Double,
)

/** Main application window position in desktop coordinates. */
data class LayoutPosition(
    val x: Double,
    val y: Double,
)

/** Persisted state for one workspace panel. */
data class LayoutWindowState(
    val id: String,
    val order: Int = 0,
    val visible: Boolean = true,
    val preferredWidth: Double = 0.0,
)
