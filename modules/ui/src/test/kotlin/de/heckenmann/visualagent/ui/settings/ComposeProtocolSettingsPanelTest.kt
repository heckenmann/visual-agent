package de.heckenmann.visualagent.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.ui.components.settingsDraftActionRow
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Verifies that UI settings are staged locally and can be reset from persisted state. */
class ComposeProtocolSettingsPanelTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `panel renders only UI settings and disabled draft actions`() {
        val settings = settingsPort(SettingsSnapshot())

        composeTestRule.setContent { MaterialTheme { settingsPanel(settings, {}) } }

        composeTestRule.onNodeWithText("Appearance").assertExists()
        composeTestRule.onNodeWithText("Conversation").assertDoesNotExist()
        composeTestRule.onNodeWithText("Save changes").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Reset changes").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Settings loaded").assertExists()
        composeTestRule.onNodeWithText("Provider connection").assertDoesNotExist()
    }

    @Test
    fun `settings draft actions expose a clear secondary reset and primary save`() {
        var resetCount = 0
        var saveCount = 0

        composeTestRule.setContent {
            MaterialTheme {
                settingsDraftActionRow(
                    hasUnsavedChanges = true,
                    saving = false,
                    onReset = { resetCount += 1 },
                    onSave = { saveCount += 1 },
                )
            }
        }

        composeTestRule.onNodeWithText("Reset changes").assertIsEnabled().performClick()
        composeTestRule.onNodeWithText("Save changes").assertIsEnabled().performClick()
        kotlin.test.assertEquals(1, resetCount)
        kotlin.test.assertEquals(1, saveCount)
    }

    @Test
    fun `reset reloads persisted settings without saving the local draft`() {
        val settings = settingsPort(SettingsSnapshot(fontSize = 16))

        composeTestRule.setContent { MaterialTheme { settingsPanel(settings, {}) } }

        composeTestRule.onNodeWithText("Reset changes").assertIsNotEnabled()
        verify(exactly = 0) { settings.save(any()) }
    }

    @Test
    fun `appearance merge preserves newer conversation settings`() {
        val current = SettingsSnapshot(fontSize = 14, contextLength = 20, timeoutSeconds = 120)
        val appearanceDraft = SettingsSnapshot(fontSize = 18, contextLength = 5, timeoutSeconds = 30)

        val merged = current.withAppearanceFrom(appearanceDraft)

        assertEquals(18, merged.fontSize)
        assertEquals(20, merged.contextLength)
        assertEquals(120, merged.timeoutSeconds)
    }

    private fun settingsPort(snapshot: SettingsSnapshot): SettingsPort =
        mockk<SettingsPort>(relaxed = true).also { port ->
            every { port.snapshot() } returns snapshot
            coEvery { port.snapshotAsync() } returns snapshot
            every { port.addChangeListener(any()) } returns AutoCloseable { }
        }
}
