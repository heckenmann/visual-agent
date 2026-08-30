package de.heckenmann.visualagent.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Verifies selection of manual and automatic UI scaling in the appearance settings section. */
class ComposeAppearanceSettingsSectionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `appearance settings select manual and automatic UI scale`() {
        var settings by mutableStateOf(SettingsSnapshot(uiScalePercent = 100))

        composeTestRule.setContent {
            MaterialTheme {
                AppearanceSettingsSection(snapshot = settings, onChange = { settings = it })
            }
        }

        composeTestRule.onNodeWithText("100%").performClick()
        composeTestRule.onNodeWithText("125%").performClick()
        assertEquals(125, settings.uiScalePercent)

        composeTestRule.onNodeWithText("125%").performClick()
        composeTestRule.onNodeWithText("Automatic").performClick()
        assertNull(settings.uiScalePercent)
    }

    @Test
    fun `appearance settings explain every editable setting`() {
        composeTestRule.setContent {
            MaterialTheme {
                AppearanceSettingsSection(snapshot = SettingsSnapshot(), onChange = {})
            }
        }

        listOf("Font size", "UI scale", "Theme", "Show panel labels").forEach { label ->
            composeTestRule.onNodeWithContentDescription("$label information").assertExists()
        }
    }
}
