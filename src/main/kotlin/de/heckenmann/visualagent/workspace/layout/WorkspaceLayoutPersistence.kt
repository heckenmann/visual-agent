package de.heckenmann.visualagent.workspace.layout

import de.heckenmann.visualagent.knowledge.PreferenceStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service

/**
 * Persists the internal workspace window arrangement in the application preference store.
 */
@Service
class WorkspaceLayoutPersistence(
    private val preferenceStore: PreferenceStore,
) {
    /** Loads the persisted workspace layout or an empty layout when none is stored. */
    fun load(): WorkspaceLayout =
        preferenceStore
            .getPreference(KEY)
            ?.let { value -> runCatching { json.decodeFromString<WorkspaceLayout>(value) }.getOrNull() }
            ?: WorkspaceLayout()

    /** Stores the supplied workspace layout. */
    fun save(layout: WorkspaceLayout) {
        preferenceStore.setPreference(KEY, json.encodeToString(layout))
    }

    private companion object {
        const val KEY = "ui.workspace.layout.v1"
        val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
    }
}

/**
 * Serializable workspace arrangement containing every known internal window.
 */
@Serializable
data class WorkspaceLayout(
    /** Persisted states of workspace windows. */
    val windows: List<WorkspaceWindowState> = emptyList(),
    /** Persisted main application window size. */
    val stage: StageState? = null,
)

/**
 * Serializable state for one internal workspace window.
 *
 * The Compose desktop shell renders panels in a single horizontal row, so the
 * persisted geometry is reduced to order, visibility, and preferred width.
 * Legacy `x`, `y`, `width`, `height`, and `zIndex` fields are ignored on load.
 */
@Serializable
data class WorkspaceWindowState(
    /** Stable workspace window identifier. */
    val id: String,
    /** Panel position in the horizontal row, starting at 0. */
    val order: Int = 0,
    /** Whether the panel is visible in the row. */
    val visible: Boolean = true,
    /** User-defined preferred panel width in the row layout. */
    val preferredWidth: Double = 0.0,
    /** Obsolete window x-coordinate inside the desktop. */
    @Deprecated("Horizontal row layout no longer uses coordinates")
    val x: Double = 0.0,
    /** Obsolete window y-coordinate inside the desktop. */
    @Deprecated("Horizontal row layout no longer uses coordinates")
    val y: Double = 0.0,
    /** Obsolete window width in pixels. */
    @Deprecated("Use preferredWidth instead")
    val width: Double = 0.0,
    /** Obsolete window height in pixels. */
    @Deprecated("Horizontal row layout uses full row height")
    val height: Double = 0.0,
    /** Obsolete z-order index. */
    @Deprecated("Use order instead")
    val zIndex: Int = 0,
)
