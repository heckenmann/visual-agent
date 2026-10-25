package de.heckenmann.visualagent.ui.conversation.live

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.model.ToolLiveProgress
import de.heckenmann.visualagent.model.ToolLiveSeries
import de.heckenmann.visualagent.model.ToolLiveState
import de.heckenmann.visualagent.model.ToolLiveTable
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ToolLivePanelTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `semantic progress logs and tables update in place and terminal state removes cancel`() {
        var state by mutableStateOf(
            ToolLiveState(
                "execution",
                1,
                "Running",
                progress = ToolLiveProgress(4, 10, "rows"),
                logs = listOf("First output"),
                table = ToolLiveTable(listOf("Name"), listOf(listOf("Alice"))),
            ),
        )
        var cancelled = 0
        state = state.copy(series = listOf(ToolLiveSeries("Throughput", listOf(2.5, 4.0), "items/s")))
        compose.setContent { MaterialTheme { ToolLivePanel(state, onCancel = { cancelled++ }) } }
        compose.onNodeWithText("Running").assertExists()
        compose.onNodeWithText("4 / 10 rows").assertExists()
        compose.onNodeWithText("First output").assertExists()
        compose.onNodeWithText("Alice").assertExists()
        compose.onNodeWithText("Throughput").assertExists()
        compose.onNodeWithText("4.0 items/s").assertExists()
        compose.onNodeWithContentDescription("Cancel tool execution").performClick()
        compose.runOnIdle {
            assertEquals(1, cancelled)
            state = state.copy(revision = 2, status = "Completed", terminal = true)
        }
        compose.onNodeWithText("Completed").assertExists()
        compose.onNodeWithContentDescription("Cancel tool execution").assertDoesNotExist()
    }
}
