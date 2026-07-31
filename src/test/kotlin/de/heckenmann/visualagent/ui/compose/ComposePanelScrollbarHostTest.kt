package de.heckenmann.visualagent.ui.compose

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
