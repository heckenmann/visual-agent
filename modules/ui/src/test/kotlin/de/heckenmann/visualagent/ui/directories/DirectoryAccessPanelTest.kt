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
import de.heckenmann.visualagent.ui.CompletionIdlingResource
import de.heckenmann.visualagent.ui.awaitCompletion
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
        val grantsLoaded = CompletionIdlingResource("directory grants")
        every { directoryAccess.listGrants() } answers {
            grantsLoaded.complete()
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
        }
        composeTestRule.awaitCompletion(grantsLoaded) {
            composeTestRule.setContent {
                MaterialTheme {
                    DirectoryAccessPanel(
                        directoryAccess,
                        mockk<ClientDirectoryGrantAdministrationPort>(relaxed = true),
                        ComposeModalRequester { },
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("This device").assertExists()
        assertEquals(2, composeTestRule.onAllNodesWithText("Application server").fetchSemanticsNodes().size)
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
        val grantsLoaded = CompletionIdlingResource("directory grants")
        every { directoryAccess.listGrants() } answers {
            grantsLoaded.complete()
            listOf(grant)
        }
        every { directoryAccess.updateGrant("grant", "Documents", DirectoryAccessMode.READ_ONLY) } returns grant

        composeTestRule.awaitCompletion(grantsLoaded) {
            composeTestRule.setContent {
                MaterialTheme {
                    DirectoryAccessPanel(
                        directoryAccess,
                        mockk<ClientDirectoryGrantAdministrationPort>(relaxed = true),
                        ComposeModalRequester { },
                    )
                }
            }
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
        val rootsLoaded = CompletionIdlingResource("directory roots")
        val firstPageLoaded = CompletionIdlingResource("directory child page")
        val secondPageLoaded = CompletionIdlingResource("next directory child page")
        every { directoryAccess.listServerDirectoryRoots(null) } answers {
            rootsLoaded.complete()
            ServerDirectoryPickerPage(listOf(root), "")
        }
        every { directoryAccess.listServerDirectoryChildren("root", null) } answers {
            firstPageLoaded.complete()
            ServerDirectoryPickerPage(listOf(child), "next")
        }
        every { directoryAccess.listServerDirectoryChildren("root", "next") } answers {
            secondPageLoaded.complete()
            ServerDirectoryPickerPage(listOf(nextChild), null)
        }
        var selected: ServerDirectoryPickerEntry? = null
        var dismissed = false

        composeTestRule.awaitCompletion(rootsLoaded) {
            composeTestRule.setContent {
                MaterialTheme {
                    ServerDirectoryPicker(directoryAccess, { selected = it }, { dismissed = true })
                }
            }
        }
        composeTestRule.awaitCompletion(firstPageLoaded) {
            composeTestRule.onNodeWithContentDescription("Open Root folder").performClick()
        }
        composeTestRule.onNodeWithText("Filter folders").performTextInput("folder")
        composeTestRule.awaitCompletion(secondPageLoaded) {
            composeTestRule.onNodeWithText("Load more folders").performClick()
        }
        composeTestRule.onNodeWithText("Choose this folder").performClick()
        assertEquals(root, selected)
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun `server picker exposes load failures`() {
        val directoryAccess = mockk<DirectoryGrantAdministrationPort>()
        val rootsRequested = CompletionIdlingResource("failed directory roots request")
        every { directoryAccess.listServerDirectoryRoots(null) } answers {
            rootsRequested.complete()
            throw IllegalStateException("server unavailable")
        }

        composeTestRule.awaitCompletion(rootsRequested) {
            composeTestRule.setContent {
                MaterialTheme {
                    ServerDirectoryPicker(directoryAccess, {}, {})
                }
            }
        }
        composeTestRule.onNodeWithText("server unavailable").assertExists()
    }
}
