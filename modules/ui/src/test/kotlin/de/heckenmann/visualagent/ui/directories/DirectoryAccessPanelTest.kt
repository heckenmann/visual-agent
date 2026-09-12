package de.heckenmann.visualagent.ui.directories

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.heckenmann.visualagent.protocol.ClientDirectoryGrantAdministrationPort
import de.heckenmann.visualagent.protocol.DirectoryAccessMode
import de.heckenmann.visualagent.protocol.DirectoryGrantAdministrationPort
import de.heckenmann.visualagent.protocol.DirectoryGrantOrigin
import de.heckenmann.visualagent.protocol.DirectoryGrantView
import de.heckenmann.visualagent.protocol.ServerDirectoryPickerEntry
import de.heckenmann.visualagent.protocol.ServerDirectoryPickerPage
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies that the directory grant panel exposes its origin and access choices. */
class DirectoryAccessPanelTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `panel renders origin and access descriptions`() {
        val directoryAccess = mockk<DirectoryGrantAdministrationPort>(relaxed = true)
        every { directoryAccess.listGrants() } returns
            listOf(
                DirectoryGrantView(
                    "grant",
                    "Documents",
                    DirectoryGrantOrigin.SERVER,
                    "Application server",
                    DirectoryAccessMode.READ_ONLY,
                    "/srv/documents",
                    true,
                    null,
                ),
            )
        composeTestRule.setContent {
            MaterialTheme {
                DirectoryAccessPanel(
                    directoryAccess,
                    mockk<ClientDirectoryGrantAdministrationPort>(relaxed = true),
                    ComposeModalRequester { },
                )
            }
        }
        composeTestRule.onNodeWithText("This device").assertExists()
        assertEquals(2, composeTestRule.onAllNodesWithText("Application server").fetchSemanticsNodes().size)
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Documents").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(composeTestRule.onAllNodesWithText("Documents").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `selectors render both choices and update selection`() {
        composeTestRule.setContent {
            MaterialTheme {
                Column {
                    DirectoryGrantOriginSelector(DirectoryGrantOrigin.SERVER) { }
                    DirectoryAccessModeSelector(DirectoryAccessMode.READ_ONLY) { }
                }
            }
        }

        composeTestRule.onNodeWithText("This device").performClick()
        composeTestRule.onNodeWithText("Read and write").performClick()
        assertTrue(composeTestRule.onAllNodesWithText("Application server").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("Read only").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `panel edits an existing grant and switches the directory origin`() {
        val directoryAccess = mockk<DirectoryGrantAdministrationPort>(relaxed = true)
        val grant =
            DirectoryGrantView(
                "grant",
                "Documents",
                DirectoryGrantOrigin.SERVER,
                "Application server",
                DirectoryAccessMode.READ_ONLY,
                "/srv/documents",
                true,
                null,
            )
        every { directoryAccess.listGrants() } returns listOf(grant)
        every { directoryAccess.updateGrant("grant", "Documents", DirectoryAccessMode.READ_ONLY) } returns grant

        composeTestRule.setContent {
            MaterialTheme {
                DirectoryAccessPanel(
                    directoryAccess,
                    mockk<ClientDirectoryGrantAdministrationPort>(relaxed = true),
                    ComposeModalRequester { },
                )
            }
        }
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Save changes").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("This device").performClick()
        composeTestRule.onNodeWithText("This device directory").assertExists()
        composeTestRule.onAllNodesWithText("Save changes").get(0).performClick()
    }

    @Test
    fun `server picker browses pages and reports the selected folder`() {
        val directoryAccess = mockk<DirectoryGrantAdministrationPort>()
        val root = ServerDirectoryPickerEntry("root", "Root folder", "/srv/root", null)
        val child = ServerDirectoryPickerEntry("child", "Child folder", "/srv/root/child", "root")
        val nextChild = ServerDirectoryPickerEntry("next-child", "Another folder", "/srv/root/another", "root")
        every { directoryAccess.listServerDirectoryRoots(null) } returns ServerDirectoryPickerPage(listOf(root), "")
        every { directoryAccess.listServerDirectoryChildren("root", null) } returns
            ServerDirectoryPickerPage(listOf(child), "next")
        every { directoryAccess.listServerDirectoryChildren("root", "next") } returns
            ServerDirectoryPickerPage(listOf(nextChild), null)
        var selected: ServerDirectoryPickerEntry? = null
        var dismissed = false

        composeTestRule.setContent {
            MaterialTheme {
                ServerDirectoryPicker(directoryAccess, { selected = it }, { dismissed = true })
            }
        }
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Root folder").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Open Root folder").performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Child folder").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Filter folders").performTextInput("folder")
        composeTestRule.onNodeWithText("Load more folders").performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Another folder").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Choose this folder").performClick()
        assertEquals(root, selected)
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun `server picker exposes load failures`() {
        val directoryAccess = mockk<DirectoryGrantAdministrationPort>()
        every { directoryAccess.listServerDirectoryRoots(null) } throws IllegalStateException("server unavailable")

        composeTestRule.setContent {
            MaterialTheme {
                ServerDirectoryPicker(directoryAccess, {}, {})
            }
        }
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("server unavailable").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
