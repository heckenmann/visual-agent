package de.heckenmann.visualagent.ui.files

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.protocol.WorkspaceDownload
import de.heckenmann.visualagent.protocol.WorkspaceDownloadState
import de.heckenmann.visualagent.protocol.WorkspaceFilePort
import de.heckenmann.visualagent.ui.CompletionIdlingResource
import de.heckenmann.visualagent.ui.awaitCompletion
import de.heckenmann.visualagent.ui.modal.ComposeModalRequester
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Covers blocked download operations and progress refreshes through the Files panel. */
class WorkspaceDownloadControlsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `download controls keep the panel responsive and apply transitions in order`() {
        val workspace = mockk<WorkspaceFilePort>(relaxed = true)
        val initial = WorkspaceDownload("report", "report.bin", WorkspaceDownloadState.DOWNLOADING, 50, 100)
        val downloads = AtomicReference(listOf(initial))
        val loads = CompletionIdlingResource("workspace downloads")
        every { workspace.activeDownloads() } answers {
            loads.complete()
            downloads.get()
        }
        every { workspace.listFiles() } returns emptyList()
        every { workspace.listDirectories() } returns emptyList()
        every { workspace.workspaceRoot() } returns "/workspace"
        every { workspace.addDownloadListener(any()) } returns AutoCloseable { }
        every { workspace.addListener(any()) } returns AutoCloseable { }
        composeTestRule.awaitCompletion(loads) {
            composeTestRule.setContent {
                MaterialTheme {
                    FilesPanel(workspace, mockk(relaxed = true), ComposeModalRequester {}, mockk(relaxed = true))
                }
            }
        }

        checkBlockedControl("Pause download", { workspace.pauseDownload("report") }) {
            downloads.set(listOf(initial.copy(state = WorkspaceDownloadState.PAUSED)))
        }
        composeTestRule.waitUntil(5_000) {
            runCatching { composeTestRule.onNodeWithContentDescription("Resume download").assertExists() }.isSuccess
        }

        checkBlockedControl("Resume download", { workspace.resumeDownload("report") }) { downloads.set(listOf(initial)) }
        composeTestRule.waitUntil(5_000) {
            runCatching { composeTestRule.onNodeWithContentDescription("Pause download").assertExists() }.isSuccess
        }

        checkBlockedControl("Cancel download", { workspace.cancelDownload("report") }) { error("Port unavailable") }
        composeTestRule.waitUntil(5_000) {
            runCatching { composeTestRule.onNodeWithText("Could not cancel download: Port unavailable").assertExists() }.isSuccess
        }

        every { workspace.cancelDownload("report") } answers { downloads.set(emptyList()) }
        composeTestRule.onNodeWithContentDescription("Cancel download").performClick()
        composeTestRule.waitUntil(5_000) {
            runCatching { composeTestRule.onNodeWithText("No matching files").assertExists() }.isSuccess
        }
        verify(exactly = 2) { workspace.cancelDownload("report") }
    }

    @Test
    fun `progress events during a blocked refresh request one follow-up snapshot`() {
        val workspace = mockk<WorkspaceFilePort>(relaxed = true)
        val listener = slot<() -> Unit>()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reads = AtomicInteger()
        every { workspace.addDownloadListener(capture(listener)) } returns AutoCloseable { }
        every { workspace.addListener(any()) } returns AutoCloseable { }
        every { workspace.listFiles() } returns emptyList()
        every { workspace.listDirectories() } returns emptyList()
        every { workspace.workspaceRoot() } returns "/workspace"
        every { workspace.activeDownloads() } answers {
            val read = reads.incrementAndGet()
            if (read == 1) {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
            }
            listOf(WorkspaceDownload("report", "report.bin", WorkspaceDownloadState.DOWNLOADING, read.toLong(), 100))
        }
        try {
            composeTestRule.setContent {
                MaterialTheme {
                    FilesPanel(workspace, mockk(relaxed = true), ComposeModalRequester {}, mockk(relaxed = true))
                }
            }
            composeTestRule.waitForIdle()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            repeat(50) { listener.captured() }
            assertEquals(1, reads.get())
        } finally {
            release.countDown()
        }
        composeTestRule.waitUntil(5_000) {
            runCatching { composeTestRule.onNodeWithText("2 / 100 bytes").assertExists() }.isSuccess
        }
        assertEquals(2, reads.get())
    }

    private fun checkBlockedControl(
        description: String,
        call: io.mockk.MockKMatcherScope.() -> Unit,
        complete: () -> Unit,
    ) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        every(call) answers {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS)) { "The panel never completed the responsiveness check." }
            complete()
        }
        try {
            composeTestRule.onNodeWithContentDescription(description).performClick()
            composeTestRule.waitForIdle()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            composeTestRule.onNodeWithContentDescription(description).assertIsNotEnabled()
            composeTestRule.onNodeWithText("Search files").assertExists()
        } finally {
            release.countDown()
        }
        verify(exactly = 1) { call() }
    }
}
