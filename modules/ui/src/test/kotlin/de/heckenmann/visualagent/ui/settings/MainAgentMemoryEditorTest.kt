package de.heckenmann.visualagent.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import de.heckenmann.visualagent.protocol.MainAgentMemoryPort
import de.heckenmann.visualagent.protocol.MainAgentMemorySnapshot
import de.heckenmann.visualagent.protocol.MainAgentMemoryUpdate
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.ui.settings.agent.AgentSettingsPage
import de.heckenmann.visualagent.ui.settings.agent.MainAgentMemoryEditor
import de.heckenmann.visualagent.ui.settings.navigation.SettingsPageDraft
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test

/** Covers independent revisioned memory saves and explicit conflict recovery. */
class MainAgentMemoryEditorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `conflicts retain local memory and retry uses the current revision before explicit reload`() {
        val port = mockk<MainAgentMemoryPort>()
        val initial = MainAgentMemorySnapshot("Original", contentLength = 8, limit = 12000, revision = 1)
        val concurrent = MainAgentMemorySnapshot("Remote", contentLength = 6, limit = 12000, revision = 2)
        val saved = MainAgentMemorySnapshot("My draft", contentLength = 8, limit = 12000, revision = 3)
        every { port.snapshot() } returns initial andThen saved
        every { port.replace("My draft", 1) } returns MainAgentMemoryUpdate.Conflict(concurrent)
        every { port.replace("My draft", 2) } returns MainAgentMemoryUpdate.Saved(saved)
        compose.setContent { MaterialTheme { MainAgentMemoryEditor(port) } }
        compose.waitUntil { runCatching { compose.onNodeWithText("Original").assertExists() }.isSuccess }
        compose.onNodeWithText("Original").performTextReplacement("My draft")
        compose.onNodeWithContentDescription("Save main-agent memory").performClick()
        compose.waitUntil {
            runCatching {
                compose.onNodeWithText("Memory changed elsewhere. Your draft is retained; review it before saving again.").assertExists()
            }.isSuccess
        }
        compose.onNodeWithText("My draft").assertExists()
        compose.onNodeWithContentDescription("Save main-agent memory").performClick()
        compose.waitUntil { runCatching { compose.onNodeWithText("Saved main-agent memory").assertExists() }.isSuccess }
        compose.onNodeWithText("My draft").performTextReplacement("Discard this")
        compose.onNodeWithContentDescription("Reload main-agent memory").performClick()
        compose.waitUntil { runCatching { compose.onNodeWithText("My draft").assertExists() }.isSuccess }
        verify(exactly = 1) { port.replace("My draft", 1) }
        verify(exactly = 1) { port.replace("My draft", 2) }
        verify(exactly = 2) { port.snapshot() }
    }

    @Test fun `saving a greater memory limit immediately enables a larger document without reloading`() {
        val port = mockk<MainAgentMemoryPort>()
        val settings = mockk<SettingsPort>(relaxed = true)
        val state = SettingsPageDraft()
        coEvery { settings.snapshotAsync() } returns SettingsSnapshot(maxMainAgentMemoryChars = 1000)
        every { port.snapshot() } returns MainAgentMemorySnapshot("", contentLength = 0, limit = 1000, revision = 1)
        compose.setContent { MaterialTheme { AgentSettingsPage(settings, port, state, onSaved = {}) } }
        compose.waitUntil { state.loaded.value }
        compose.onNodeWithContentDescription("Main-agent memory limit").performScrollTo().performTextReplacement("2000")
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil { state.persisted.value.maxMainAgentMemoryChars == 2000 }
        compose.onNodeWithText("Durable model reference").performScrollTo().performTextReplacement("x".repeat(1500))
        compose.onNodeWithContentDescription("Save main-agent memory").performScrollTo().assertIsEnabled()
    }
}
