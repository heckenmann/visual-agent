package de.heckenmann.visualagent.agent.tools

import de.heckenmann.visualagent.knowledge.PreferenceStore
import de.heckenmann.visualagent.ui.WorkspaceLayout
import de.heckenmann.visualagent.ui.WorkspaceLayoutPersistence
import de.heckenmann.visualagent.ui.WorkspaceLayoutService
import de.heckenmann.visualagent.ui.WorkspaceWindowState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceLayoutToolTest {
    @Test
    fun `get returns persisted window layout when live ui is unavailable`() {
        val persistence = WorkspaceLayoutPersistence(MapPreferenceStore())
        persistence.save(
            WorkspaceLayout(
                listOf(WorkspaceWindowState("conversation", 10.0, 20.0, 640.0, 480.0, visible = true, zIndex = 1)),
            ),
        )
        val tool = WorkspaceLayoutTool(WorkspaceLayoutService(persistence))

        val result = tool.execute("""{"action":"get"}""")
        val content = Json.parseToJsonElement(result.content).jsonObject
        val window = content["windows"]!!.jsonArray.single().jsonObject

        assertTrue(result.success)
        assertEquals("conversation", window["id"]!!.jsonPrimitive.content)
        assertEquals(10.0, window["x"]!!.jsonPrimitive.double)
        assertEquals(640.0, window["width"]!!.jsonPrimitive.double)
        assertTrue(window["visible"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `set merges partial window patch and persists layout`() {
        val persistence = WorkspaceLayoutPersistence(MapPreferenceStore())
        persistence.save(
            WorkspaceLayout(
                listOf(WorkspaceWindowState("conversation", 10.0, 20.0, 640.0, 480.0, visible = true, zIndex = 1)),
            ),
        )
        val tool = WorkspaceLayoutTool(WorkspaceLayoutService(persistence))

        val result =
            tool.execute(
                """{"action":"set","windows":[{"id":"conversation","x":30,"y":40,"visible":false}]}""",
            )
        val saved = persistence.load().windows.single()

        assertTrue(result.success)
        assertEquals(30.0, saved.x)
        assertEquals(40.0, saved.y)
        assertEquals(640.0, saved.width)
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
