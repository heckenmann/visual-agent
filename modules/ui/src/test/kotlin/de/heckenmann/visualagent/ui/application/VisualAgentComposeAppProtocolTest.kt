package de.heckenmann.visualagent.ui.application

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.AgentPort
import de.heckenmann.visualagent.protocol.ApplicationPort
import de.heckenmann.visualagent.protocol.CanvasPort
import de.heckenmann.visualagent.protocol.ConversationHistoryPage
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.ConversationPreferences
import de.heckenmann.visualagent.protocol.LayoutWindowState
import de.heckenmann.visualagent.protocol.ProviderPort
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.protocol.TodoPort
import de.heckenmann.visualagent.protocol.WorkspaceFilePort
import de.heckenmann.visualagent.protocol.WorkspaceLayoutPort
import de.heckenmann.visualagent.protocol.WorkspaceLayoutSnapshot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies that the ready workspace composes every panel through protocol ports. */
class VisualAgentComposeAppProtocolTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `workspace renders all persisted panels without application imports`() {
        val port = protocolPort()
        val allPanels =
            listOf("chat", "todos", "files", "agents", "settings", "canvas").mapIndexed { index, id ->
                LayoutWindowState(id = id, order = index, visible = true, preferredWidth = 360.0)
            }

        composeTestRule.setContent {
            MaterialTheme {
                VisualAgentComposeApp(
                    deps = ComposeApplicationDependencies(port),
                    onCloseApplication = {},
                    persistedWindows = allPanels,
                )
            }
        }

        composeTestRule.waitForIdle()
        assertEquals(2, composeTestRule.onAllNodesWithText("Conversation").fetchSemanticsNodes().size)
        assertEquals(2, composeTestRule.onAllNodesWithText("Todos").fetchSemanticsNodes().size)
        assertEquals(2, composeTestRule.onAllNodesWithText("Files").fetchSemanticsNodes().size)
        assertTrue(composeTestRule.onAllNodesWithText("Subagents").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("Canvas").fetchSemanticsNodes().isNotEmpty())
    }

    private fun protocolPort(): ApplicationPort {
        val conversation = mockk<ConversationPort>(relaxed = true)
        coEvery { conversation.currentHistory() } returns emptyList()
        every { conversation.preferences() } returns ConversationPreferences()
        coEvery { conversation.latest() } returns ConversationHistoryPage(emptyList(), 0, false)
        coEvery { conversation.older(any()) } returns ConversationHistoryPage(emptyList(), 0, false)

        val todos = mockk<TodoPort>(relaxed = true)
        every { todos.list() } returns emptyList()
        every { todos.agents() } returns emptyList()
        every { todos.addListener(any()) } returns AutoCloseable { }
        every { todos.addProgressListener(any()) } returns AutoCloseable { }

        val agents = mockk<AgentPort>(relaxed = true)
        every { agents.list() } returns emptyList()
        every { agents.addExecutionListener(any()) } returns AutoCloseable { }
        every { agents.addChangeListener(any()) } returns AutoCloseable { }
        every { agents.executionSnapshot() } returns
            de.heckenmann.visualagent.protocol
                .AgentExecutionSnapshot(false)

        val providers = mockk<ProviderPort>(relaxed = true)
        every { providers.activeProviderId() } returns "ollama"
        every { providers.activeModelId() } returns "llama"
        every { providers.enabledProviders() } returns emptyList()
        every { providers.addChangeListener(any()) } returns AutoCloseable { }

        val settings = mockk<SettingsPort>(relaxed = true)
        every { settings.snapshot() } returns SettingsSnapshot()
        coEvery { settings.snapshotAsync() } returns SettingsSnapshot()
        every { settings.addChangeListener(any()) } returns AutoCloseable { }

        val files = mockk<WorkspaceFilePort>(relaxed = true)
        every { files.listFiles() } returns emptyList()
        every { files.workspaceRoot() } returns "workspace"
        every { files.addListener(any()) } returns AutoCloseable { }

        val canvas = mockk<CanvasPort>(relaxed = true)
        every { canvas.snapshot() } returns
            de.heckenmann.visualagent.protocol
                .CanvasSnapshot(0, 100, true, figures = emptyList())
        val layout = mockk<WorkspaceLayoutPort>(relaxed = true)
        every { layout.report() } returns WorkspaceLayoutSnapshot()
        every { layout.addWindowStateListener(any()) } returns AutoCloseable { }
        val activity = mockk<ActivityPort>(relaxed = true)
        every { activity.addToolListener(any()) } returns AutoCloseable { }
        every { activity.addAgentListener(any()) } returns AutoCloseable { }
        val application = mockk<ApplicationPort>()
        every { application.conversation } returns conversation
        every { application.todos } returns todos
        every { application.agents } returns agents
        every { application.providers } returns providers
        every { application.settings } returns settings
        every { application.workspaceFiles } returns files
        every { application.canvas } returns canvas
        every { application.layout } returns layout
        every { application.activity } returns activity
        every { application.lifecycle } returns mockk(relaxed = true)
        return application
    }
}
