@file:Suppress("ktlint:standard:no-wildcard-imports")

package de.heckenmann.visualagent.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.ui.agents.*
import de.heckenmann.visualagent.ui.application.*
import de.heckenmann.visualagent.ui.canvas.*
import de.heckenmann.visualagent.ui.components.*
import de.heckenmann.visualagent.ui.conversation.*
import de.heckenmann.visualagent.ui.files.*
import de.heckenmann.visualagent.ui.modal.*
import de.heckenmann.visualagent.ui.settings.*
import de.heckenmann.visualagent.ui.status.*
import de.heckenmann.visualagent.ui.todo.*
import de.heckenmann.visualagent.ui.workspace.*
import org.junit.Rule
import org.junit.Test

class ComposePanelScrollbarHostTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `panel scrollbar host renders the registered vertical scroll state`() {
        composeTestRule.setContent {
            MaterialTheme {
                PanelScrollbarHost(modifier = Modifier.height(120.dp)) {
                    val scrollState = rememberScrollState()
                    RegisterPanelVerticalScrollbar(scrollState)
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                        repeat(20) { index -> Text("Scrollable item $index") }
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Scrollable item 0").assertExists()
        composeTestRule.onNodeWithContentDescription("Panel scrollbar").assertExists()
    }
}
