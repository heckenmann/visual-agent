package de.heckenmann.visualagent.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.MainAgentMemorySnapshot
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.ui.application.ComposePanelServices
import de.heckenmann.visualagent.ui.application.UpdatePresentationState
import de.heckenmann.visualagent.ui.settings.navigation.SettingsDestination
import de.heckenmann.visualagent.ui.settings.navigation.SettingsNavigationState
import de.heckenmann.visualagent.ui.settings.navigation.SettingsWorkspacePanel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

/** Settings destinations keep draft ownership and independent memory edits intact. */
class SettingsNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `last settings destination stays reachable in a narrow panel`() {
        val services = mockk<ComposePanelServices>(relaxed = true)
        val navigation = SettingsNavigationState()
        every { services.settingsNavigation } returns navigation
        every { services.updateState } returns mutableStateOf(UpdatePresentationState())
        coEvery { services.settings.snapshotAsync() } returns SettingsSnapshot()
        every { services.settings.snapshot() } returns SettingsSnapshot()
        every { services.providers.listProviders() } returns emptyList()
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(160.dp).height(480.dp)) {
                    SettingsWorkspacePanel(services)
                }
            }
        }

        val firstTab = compose.onAllNodesWithText("Appearance")[0]
        firstTab.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        val lastTab = compose.onAllNodesWithText("Updates")[0]
        lastTab.performKeyInput {
            repeat(5) {
                keyDown(Key.Tab)
                keyUp(Key.Tab)
            }
        }
        lastTab.assertIsFocused().assertIsDisplayed()
        lastTab.performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }
        lastTab.assertIsSelected().assertIsDisplayed()
        compose.onNodeWithText("Check for updates automatically").assertExists()
    }

    @Test fun `switching destinations preserves unsaved agent instructions and memory while hiding unrelated controls`() {
        val services = mockk<ComposePanelServices>(relaxed = true)
        val navigation = SettingsNavigationState()
        every { services.settingsNavigation } returns navigation
        every { services.updateState } returns mutableStateOf(UpdatePresentationState())
        coEvery { services.settings.snapshotAsync() } returns SettingsSnapshot()
        every { services.mainAgentMemory.snapshot() } returns MainAgentMemorySnapshot("", 0, 12000, 0)
        every { services.providers.listProviders() } returns emptyList()
        compose.setContent { MaterialTheme { SettingsWorkspacePanel(services) } }
        compose.onNodeWithText("Agent").performClick()
        compose.onNodeWithContentDescription("Model instruction").performTextInput("Retained instruction")
        compose.onNodeWithText("Durable model reference").performTextInput("Retained memory")
        compose.onNodeWithText("Providers").performClick()
        compose.onNodeWithContentDescription("Model instruction").assertDoesNotExist()
        compose.onNodeWithText("Durable model reference").assertDoesNotExist()
        compose.onNodeWithText("Agent").performClick()
        compose.onNodeWithContentDescription("Model instruction").assertTextContains("Retained instruction")
        compose.onNodeWithText("Retained memory").performScrollTo().assertExists()
        compose.onNodeWithText("Updates").performClick()
        compose.onNodeWithContentDescription("Model instruction").assertDoesNotExist()
        compose.runOnIdle { navigation.destination = SettingsDestination.PROVIDERS }
        compose.onNodeWithContentDescription("Add provider").assertExists()
    }

    @Test fun `an unfinished provider form survives switching settings destinations`() {
        val services = mockk<ComposePanelServices>(relaxed = true)
        val navigation = SettingsNavigationState()
        every { services.settingsNavigation } returns navigation
        every { services.updateState } returns mutableStateOf(UpdatePresentationState())
        coEvery { services.settings.snapshotAsync() } returns SettingsSnapshot()
        every { services.settings.snapshot() } returns SettingsSnapshot()
        every { services.providers.listProviders() } returns emptyList()
        compose.setContent { MaterialTheme { SettingsWorkspacePanel(services) } }
        compose.onNodeWithText("Providers").performClick()
        compose.waitUntil {
            runCatching { compose.onNodeWithText("Provider settings loaded").assertExists() }.isSuccess
        }
        compose.onNodeWithContentDescription("Add provider").performClick()
        compose.onNodeWithText("Name").performTextReplacement("Unfinished profile")
        compose.onNodeWithText("Base URL").performTextReplacement("https://example.invalid")
        compose.onNodeWithText("Appearance").performClick()
        compose.onNodeWithText("Providers").performClick()
        compose.onNodeWithText("Name").assertTextContains("Unfinished profile")
        compose.onNodeWithText("Base URL").assertTextContains("https://example.invalid")
    }
}
