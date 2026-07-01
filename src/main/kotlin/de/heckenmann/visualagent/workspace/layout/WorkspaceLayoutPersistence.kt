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
)

/**
 * Serializable state for one internal workspace window.
 */
@Serializable
data class WorkspaceWindowState(
    /** Stable workspace window identifier. */
    val id: String,
    /** Window x-coordinate inside the desktop. */
    val x: Double,
    /** Window y-coordinate inside the desktop. */
    val y: Double,
    /** Window width in pixels. */
    val width: Double,
    /** Window height in pixels. */
    val height: Double,
    /** Whether the window was visible when the layout was saved. */
    val visible: Boolean,
    /** Relative z-order index, larger values are closer to the front. */
    val zIndex: Int,
)
