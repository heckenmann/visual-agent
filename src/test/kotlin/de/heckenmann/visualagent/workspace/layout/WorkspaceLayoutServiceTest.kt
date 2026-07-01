package de.heckenmann.visualagent.workspace.layout

import de.heckenmann.visualagent.knowledge.PreferenceStore
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceLayoutServiceTest {
    @Test
    fun `report returns persisted windows before live ui binds`() {
        val persistence = WorkspaceLayoutPersistence(MapPreferenceStore())
        persistence.save(
            WorkspaceLayout(
                windows =
                    listOf(
                        WorkspaceWindowState(
                            id = "conversation",
                            x = 12.0,
                            y = 24.0,
                            width = 640.0,
                            height = 480.0,
                            visible = true,
                            zIndex = 2,
                        ),
                    ),
            ),
        )

        val report = WorkspaceLayoutService(persistence).report()

        assertEquals(null, report.stage)
        assertEquals(null, report.desktop)
        assertEquals("conversation", report.windows.single().id)
        assertTrue(report.screens.isEmpty())
    }

    @Test
    fun `bind makes live workspace geometry available`() {
        val service = WorkspaceLayoutService(WorkspaceLayoutPersistence(MapPreferenceStore()))
        val windows =
            listOf(
                WorkspaceWindowState(
                    id = "files",
                    x = 100.0,
                    y = 120.0,
                    width = 400.0,
                    height = 300.0,
                    visible = true,
                    zIndex = 4,
                ),
            )

        service.bind(
            stage = StageState(width = 1280.0, height = 820.0),
            desktop = DesktopState(width = 1180.0, height = 760.0),
            windows = windows,
        )
        val report = service.report()

        assertEquals(1280.0, report.stage?.width)
        assertEquals(760.0, report.desktop?.height)
        assertEquals("files", report.windows.single().id)
    }

    @Test
    fun `apply window states persists and updates live report`() {
        val persistence = WorkspaceLayoutPersistence(MapPreferenceStore())
        val service = WorkspaceLayoutService(persistence)
        val states =
            listOf(
                WorkspaceWindowState(
                    id = "settings",
                    x = 20.0,
                    y = 30.0,
                    width = 500.0,
                    height = 360.0,
                    visible = false,
                    zIndex = 1,
                ),
            )

        val layout = service.applyWindowStates(states)

        assertEquals(states, layout.windows)
        assertEquals(states, service.report().windows)
        assertEquals(states, persistence.load().windows)
    }

    @Test
    fun `apply window states notifies registered listeners`() {
        val service = WorkspaceLayoutService(WorkspaceLayoutPersistence(MapPreferenceStore()))
        val states =
            listOf(
                WorkspaceWindowState(
                    id = "chat",
                    x = 0.0,
                    y = 0.0,
                    width = 720.0,
                    height = 520.0,
                    visible = true,
                    zIndex = 0,
                ),
            )
        var observed: List<WorkspaceWindowState> = emptyList()

        val handle = service.addWindowStateListener { observed = it }
        service.applyWindowStates(states)
        handle.close()
        service.applyWindowStates(states.map { it.copy(width = 800.0) })

        assertEquals(states, observed)
    }

    @Test
    fun `apply window states can persist without notifying listeners`() {
        val service = WorkspaceLayoutService(WorkspaceLayoutPersistence(MapPreferenceStore()))
        val states =
            listOf(
                WorkspaceWindowState(
                    id = "chat",
                    x = 0.0,
                    y = 0.0,
                    width = 720.0,
                    height = 520.0,
                    visible = true,
                    zIndex = 0,
                ),
            )
        var notifications = 0

        service.addWindowStateListener { notifications += 1 }
        service.applyWindowStates(states, notifyListeners = false)

        assertEquals(0, notifications)
        assertEquals(states, service.report().windows)
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
