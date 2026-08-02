package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.knowledge.PreferenceStore
import de.heckenmann.visualagent.workspace.layout.WorkspaceLayout
import de.heckenmann.visualagent.workspace.layout.WorkspaceLayoutPersistence
import de.heckenmann.visualagent.workspace.layout.WorkspaceLayoutService
import de.heckenmann.visualagent.workspace.layout.WorkspaceWindowState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceLayoutToolTest {
    @Test
    fun `get returns persisted panel layout when live ui is unavailable`() {
        val persistence = WorkspaceLayoutPersistence(MapPreferenceStore())
        persistence.save(
            WorkspaceLayout(
                listOf(WorkspaceWindowState("conversation", order = 1, visible = true, preferredWidth = 640.0)),
            ),
        )
        val tool = WorkspaceLayoutTool(WorkspaceLayoutService(persistence))

        val result = tool.execute("""{"action":"get"}""")
        val content = Json.parseToJsonElement(result.content).jsonObject
        val window = content["windows"]!!.jsonArray.single().jsonObject

        assertTrue(result.success)
        assertEquals("conversation", window["id"]!!.jsonPrimitive.content)
        assertEquals(1, window["order"]!!.jsonPrimitive.int)
        assertEquals(640.0, window["preferredWidth"]!!.jsonPrimitive.double)
        assertTrue(window["visible"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `set merges partial panel patch and persists layout`() {
        val persistence = WorkspaceLayoutPersistence(MapPreferenceStore())
        persistence.save(
            WorkspaceLayout(
                listOf(WorkspaceWindowState("conversation", order = 1, visible = true, preferredWidth = 640.0)),
            ),
        )
        val tool = WorkspaceLayoutTool(WorkspaceLayoutService(persistence))

        val result =
            tool.execute(
                """{"action":"set","windows":[{"id":"conversation","order":2,"visible":false,"preferredWidth":720}]}""",
            )
        val saved = persistence.load().windows.single()

        assertTrue(result.success)
        assertEquals(2, saved.order)
        assertEquals(720.0, saved.preferredWidth)
        assertEquals(false, saved.visible)
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
