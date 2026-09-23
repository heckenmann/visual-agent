package de.heckenmann.visualagent.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import de.heckenmann.visualagent.protocol.StagedUpdate
import de.heckenmann.visualagent.protocol.UpdateAsset
import de.heckenmann.visualagent.protocol.UpdateDownloadResult
import de.heckenmann.visualagent.protocol.UpdateInstallResult
import de.heckenmann.visualagent.protocol.UpdatePort
import de.heckenmann.visualagent.protocol.UpdateRequest
import de.heckenmann.visualagent.protocol.UpdateStatus
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Verifies update discovery and explicit download/install actions in Compose settings. */
class ComposeUpdateSettingsSectionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `available update can be downloaded and installer started`() {
        val updates = FakeUpdatePort()

        composeTestRule.setContent {
            MaterialTheme {
                UpdateSettingsSection(SettingsSnapshot(), updates, onChange = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Check for updates").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Update 2.0.0 is available").assertExists()
        composeTestRule.onNodeWithContentDescription("Download available update").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Update downloaded and verified").assertExists()
        composeTestRule.onNodeWithContentDescription("Start verified update installer").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, updates.downloadCalls)
        assertEquals(1, updates.installCalls)
        assertEquals("v2.0.0", updates.downloadRequest?.releaseTag)
        assertEquals("agent.AppImage", updates.downloadRequest?.assetName)
    }

    @Test
    fun `disabled automatic checks do not run when the section is opened`() {
        val updates = FakeUpdatePort()

        composeTestRule.setContent {
            MaterialTheme {
                UpdateSettingsSection(SettingsSnapshot(automaticUpdatesEnabled = false), updates, onChange = {})
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(0, updates.checkCalls)
    }

    @Test
    fun `manual check reports when the application is up to date`() {
        val updates = FakeUpdatePort().apply { result = result.copy(updateAvailable = false, latestVersion = "1.0.0") }

        composeTestRule.setContent {
            MaterialTheme {
                UpdateSettingsSection(SettingsSnapshot(), updates, onChange = {})
            }
        }
        composeTestRule.onNodeWithContentDescription("Check for updates").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Visual Agent is up to date").assertExists()
    }

    @Test
    fun `open release page button opens the release url`() {
        val updates = FakeUpdatePort()
        var openedUrl: String? = null
        val uriHandler =
            object : UriHandler {
                override fun openUri(uri: String) {
                    openedUrl = uri
                }
            }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                MaterialTheme { UpdateSettingsSection(SettingsSnapshot(), updates, onChange = {}) }
            }
        }
        composeTestRule.onNodeWithContentDescription("Check for updates").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Open release page").performClick()

        assertEquals("https://example.test/release", openedUrl)
    }

    private class FakeUpdatePort : UpdatePort {
        var downloadCalls = 0
        var installCalls = 0
        var checkCalls = 0
        var downloadRequest: UpdateRequest? = null
        var result =
            UpdateStatus(
                currentVersion = "1.0.0",
                latestVersion = "2.0.0",
                releaseTag = "v2.0.0",
                updateAvailable = true,
                channel = "stable",
                releaseName = "Release",
                releaseNotes = "Notes",
                releaseUrl = "https://example.test/release",
                selectedAsset = UpdateAsset("agent.AppImage", 10, "a".repeat(64)),
                availableAssets = emptyList(),
            )

        override fun check(request: UpdateRequest): UpdateStatus {
            checkCalls += 1
            return result
        }

        override fun download(request: UpdateRequest): UpdateDownloadResult {
            downloadCalls += 1
            downloadRequest = request
            return UpdateDownloadResult(
                staged = StagedUpdate("stage-1", "2.0.0", UpdateAsset("agent.AppImage", 10, "a".repeat(64))),
            )
        }

        override fun install(stagedId: String): UpdateInstallResult {
            installCalls += 1
            return UpdateInstallResult(true, "Installer started")
        }
    }
}
