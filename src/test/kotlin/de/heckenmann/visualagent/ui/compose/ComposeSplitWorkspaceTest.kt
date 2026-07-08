@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import de.heckenmann.visualagent.agent.AgentManager
import de.heckenmann.visualagent.agent.LLMProvider
import de.heckenmann.visualagent.agent.config.AgentToolConfigService
import de.heckenmann.visualagent.agent.provider.ProviderCatalogService
import de.heckenmann.visualagent.agent.tools.ToolEventBus
import de.heckenmann.visualagent.agent.tools.ToolRegistry
import de.heckenmann.visualagent.canvas.CanvasOperations
import de.heckenmann.visualagent.config.AppConfig
import de.heckenmann.visualagent.testsupport.KnowledgeDbTestFactory
import de.heckenmann.visualagent.workspace.WorkspaceFileService
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ComposeSplitWorkspaceTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `split workspace renders empty state when no panels visible`() {
        val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())
        val services =
            ComposePanelServices(
                config = AppConfig.instance,
                agentManager = manager,
                llmProvider = provider,
                providerCatalogService = mockk<ProviderCatalogService>(relaxed = true),
                agentToolConfigService = AgentToolConfigService(db),
                toolRegistry = mockk<ToolRegistry>(relaxed = true),
                toolEventBus = ToolEventBus(),
                workspaceFileService = mockk<WorkspaceFileService>(relaxed = true),
                canvasOperations = mockk<CanvasOperations>(relaxed = true),
                modalRequester = ComposeModalRequester { },
                onSettingsChanged = {},
                inFlight = InFlightStateHolder(),
                lifecycle = ApplicationLifecycle(),
            )
        val windows =
            listOf(
                ComposeWorkspaceWindow(
                    id = "chat",
                    icon = "chat",
                    title = "Chat",
                    subtitle = "",
                    bounds = ComposeWorkspaceWindowBounds(0, 0, 300, 200),
                    visible = false,
                ),
            )

        composeTestRule.setContent {
            MaterialTheme {
                ComposeSplitWorkspace(
                    windows = windows,
                    panelServices = services,
                    onToggleWindow = {},
                    onReorderWindows = {},
                    onResizeWindow = { _, _ -> },
                    minPanelWidth = 200,
                    viewport = ComposeWorkspaceViewport(800, 600),
                )
            }
        }

        composeTestRule.onNodeWithText("No panels are open. Use the rail to choose a workspace panel.").assertExists()
    }

    @Test
    fun `split workspace renders visible chat panel`() {
        val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())
        val services =
            ComposePanelServices(
                config = AppConfig.instance,
                agentManager = manager,
                llmProvider = provider,
                providerCatalogService = mockk<ProviderCatalogService>(relaxed = true),
                agentToolConfigService = AgentToolConfigService(db),
                toolRegistry = mockk<ToolRegistry>(relaxed = true),
                toolEventBus = ToolEventBus(),
                workspaceFileService = mockk<WorkspaceFileService>(relaxed = true),
                canvasOperations = mockk<CanvasOperations>(relaxed = true),
                modalRequester = ComposeModalRequester { },
                onSettingsChanged = {},
                inFlight = InFlightStateHolder(),
                lifecycle = ApplicationLifecycle(),
            )
        val windows =
            listOf(
                ComposeWorkspaceWindow(
                    id = "chat",
                    icon = "chat",
                    title = "Chat",
                    subtitle = "",
                    bounds = ComposeWorkspaceWindowBounds(0, 0, 300, 200),
                    visible = true,
                ),
            )

        composeTestRule.setContent {
            MaterialTheme {
                ComposeSplitWorkspace(
                    windows = windows,
                    panelServices = services,
                    onToggleWindow = {},
                    onReorderWindows = {},
                    onResizeWindow = { _, _ -> },
                    minPanelWidth = 200,
                    viewport = ComposeWorkspaceViewport(800, 600),
                )
            }
        }

        composeTestRule.onNodeWithText("Chat").assertExists()
        composeTestRule.onNodeWithText("No conversation yet").assertExists()
    }

    @Test
    fun `split workspace renders resizer for the rightmost visible panel`() {
        val db = KnowledgeDbTestFactory.create("jdbc:sqlite::memory:")
        val provider = mockk<LLMProvider>(relaxed = true)
        val manager = AgentManager(db, provider, AgentToolConfigService(db), ToolEventBus())
        val services =
            ComposePanelServices(
                config = AppConfig.instance,
                agentManager = manager,
                llmProvider = provider,
                providerCatalogService = mockk<ProviderCatalogService>(relaxed = true),
                agentToolConfigService = AgentToolConfigService(db),
                toolRegistry = mockk<ToolRegistry>(relaxed = true),
                toolEventBus = ToolEventBus(),
                workspaceFileService = mockk<WorkspaceFileService>(relaxed = true),
                canvasOperations = mockk<CanvasOperations>(relaxed = true),
                modalRequester = ComposeModalRequester { },
                onSettingsChanged = {},
                inFlight = InFlightStateHolder(),
                lifecycle = ApplicationLifecycle(),
            )
        val windows =
            listOf(
                ComposeWorkspaceWindow(
                    id = "chat",
                    icon = "chat",
                    title = "Chat",
                    subtitle = "",
                    bounds = ComposeWorkspaceWindowBounds(0, 0, 300, 200),
                    visible = true,
                    preferredWidth = 300,
                ),
                ComposeWorkspaceWindow(
                    id = "todos",
                    icon = "todos",
                    title = "Todos",
                    subtitle = "",
                    bounds = ComposeWorkspaceWindowBounds(0, 0, 300, 200),
                    visible = true,
                    preferredWidth = 300,
                ),
            )
        val resizes = mutableListOf<Pair<String, Int>>()

        composeTestRule.setContent {
            MaterialTheme {
                ComposeSplitWorkspace(
                    windows = windows,
                    panelServices = services,
                    onToggleWindow = {},
                    onReorderWindows = {},
                    onResizeWindow = { id, width -> resizes.add(id to width) },
                    minPanelWidth = 200,
                    viewport = ComposeWorkspaceViewport(2000, 600),
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Todos").assertExists()

        // Every visible panel now renders a resizer. The rightmost one is reachable by its
        // content description; dragging it must change the rightmost (todos) panel width.
        val resizerNodes = composeTestRule.onAllNodesWithContentDescription("Resize panel")
        assertTrue(
            "Expected 2 resizers but was ${resizerNodes.fetchSemanticsNodes().size}",
            resizerNodes.fetchSemanticsNodes().size == 2,
        )
        resizerNodes[1]
            .performTouchInput {
                down(center)
                moveBy(Offset(x = 100f, y = 0f))
                up()
            }
        composeTestRule.waitForIdle()

        assertTrue(
            "Expected a resize event for the rightmost panel but got $resizes",
            resizes.any { it.first == "todos" && it.second > 300 },
        )
    }
}
