package de.heckenmann.visualagent.ui

import de.heckenmann.visualagent.knowledge.PreferenceStore
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceLayoutPersistenceTest {
    @Test
    fun `layout persistence saves and loads workspace window state`() {
        val store = MapPreferenceStore()
        val persistence = WorkspaceLayoutPersistence(store)

        persistence.save(
            WorkspaceLayout(
                listOf(
                    WorkspaceWindowState("conversation", 10.0, 20.0, 640.0, 480.0, visible = true, zIndex = 3),
                ),
            ),
        )

        val loaded = persistence.load()
        assertEquals("conversation", loaded.windows.single().id)
        assertEquals(10.0, loaded.windows.single().x)
        assertEquals(640.0, loaded.windows.single().width)
        assertTrue(loaded.windows.single().visible)
    }

    @Test
    fun `layout persistence ignores malformed stored json`() {
        val store = MapPreferenceStore()
        store.setPreference("ui.workspace.layout.v1", "not-json")

        val loaded = WorkspaceLayoutPersistence(store).load()

        assertTrue(loaded.windows.isEmpty())
    }

    private class MapPreferenceStore : PreferenceStore {
        private val values = linkedMapOf<String, String>()

        override fun getPreference(key: String): String? = values[key]

        override fun setPreference(
            key: String,
            value: String,
        ) {
            values[key] = value
        }
    }
}
