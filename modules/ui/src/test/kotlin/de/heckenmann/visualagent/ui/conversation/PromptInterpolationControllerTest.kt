package de.heckenmann.visualagent.ui.conversation

import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.SettingsPort
import de.heckenmann.visualagent.protocol.SettingsSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercises the composer request lifecycle and draft preservation. */
@OptIn(ExperimentalCoroutinesApi::class)
class PromptInterpolationControllerTest {
    private val conversation = mockk<ConversationPort>()
    private val settings = mockk<SettingsPort>()
    private var draft = "original"
    private val sent = mutableListOf<String>()
    private val statuses = mutableListOf<String>()

    private fun TestScope.controller(automatic: Boolean = false): PromptInterpolationController {
        coEvery { settings.snapshotAsync() } returns SettingsSnapshot(automaticPromptInterpolation = automatic)
        coEvery { conversation.interpolate(any(), any()) } returns "refined"
        return PromptInterpolationController(conversation, settings, backgroundScope, { draft }, { draft = it }, sent::add, statuses::add)
    }

    @Test
    fun `manual refinement leaves result for review`() =
        runTest {
            val controller = controller()
            controller.manual()
            runCurrent()
            assertEquals("refined", draft)
            assertTrue(sent.isEmpty())
            assertFalse(controller.busy.value)
            assertFalse(controller.interpolating.value)
        }

    @Test
    fun `automatic interpolation defaults off`() =
        runTest {
            val controller = controller()
            controller.submit()
            runCurrent()
            assertEquals(listOf("original"), sent)
            coVerify(exactly = 0) { conversation.interpolate(any(), any()) }
        }

    @Test
    fun `automatic double click makes one request and sends result`() =
        runTest {
            val controller = controller(true)
            controller.submit()
            controller.submit()
            runCurrent()
            assertEquals(listOf("refined"), sent)
            coVerify(exactly = 1) { conversation.interpolate(any(), any()) }
        }

    @Test
    fun `manual preparation is reused until edited`() =
        runTest {
            val controller = controller(true)
            controller.manual()
            runCurrent()
            controller.submit()
            runCurrent()
            coVerify(exactly = 1) { conversation.interpolate(any(), any()) }
            draft = "new draft"
            controller.edited()
            controller.submit()
            runCurrent()
            coVerify(exactly = 2) { conversation.interpolate(any(), any()) }
            assertEquals(listOf("refined", "refined"), sent)
        }

    @Test
    fun `repeated manual requests refine again without sending`() =
        runTest {
            val controller = controller()
            controller.manual()
            runCurrent()
            controller.manual()
            runCurrent()
            coVerify(exactly = 2) { conversation.interpolate(any(), any()) }
            assertTrue(sent.isEmpty())
        }

    @Test
    fun `cancel preserves draft and stops busy state`() =
        runTest {
            val controller = controller(true)
            val response = CompletableDeferred<String>()
            coEvery { conversation.interpolate(any(), any()) } coAnswers { response.await() }
            controller.submit()
            runCurrent()
            assertTrue(controller.busy.value)
            assertTrue(controller.interpolating.value)
            controller.cancel()
            response.complete("late")
            runCurrent()
            assertEquals("original", draft)
            assertTrue(sent.isEmpty())
            assertFalse(controller.busy.value)
            assertTrue(statuses.last().contains("cancelled"))
        }

    @Test
    fun `late response cannot overwrite edited text`() =
        runTest {
            val controller = controller()
            val response = CompletableDeferred<String>()
            coEvery { conversation.interpolate(any(), any()) } coAnswers { withContext(NonCancellable) { response.await() } }
            controller.manual()
            runCurrent()
            draft = "new draft"
            controller.edited()
            response.complete("old result")
            runCurrent()
            assertEquals("new draft", draft)
            assertFalse(controller.busy.value)
        }

    @Test
    fun `settings read failure sends the original prompt instead of blocking chat`() =
        runTest {
            val controller = controller()
            coEvery { settings.snapshotAsync() } throws IllegalStateException("settings unavailable")
            controller.submit()
            runCurrent()
            assertEquals(listOf("original"), sent)
            assertTrue(controller.notice.value.contains("sending the original prompt"))
            assertFalse(controller.busy.value)
            coVerify(exactly = 0) { conversation.interpolate(any(), any()) }
        }

    @Test
    fun `settings changes reuse the current preference without an extra lookup`() =
        runTest {
            val controller = controller()
            coEvery { settings.snapshotAsync() } throws IllegalStateException("should not be consulted")
            controller.onSettingsChanged(false)
            controller.submit()
            runCurrent()
            assertEquals(listOf("original"), sent)
            controller.onSettingsChanged(true)
            draft = "another prompt"
            controller.submit()
            runCurrent()
            assertEquals(listOf("original", "refined"), sent)
            coVerify(exactly = 0) { settings.snapshotAsync() }
            coVerify(exactly = 1) { conversation.interpolate(any(), any()) }
        }

    @Test
    fun `invalid results and provider failures preserve draft and hide details`() =
        runTest {
            val controller = controller(true)
            for (result in listOf("", "x".repeat(16_385))) {
                coEvery { conversation.interpolate(any(), any()) } returns result
                controller.submit()
                runCurrent()
                assertEquals("original", draft)
                assertTrue(sent.isEmpty())
            }
            coEvery { conversation.interpolate(any(), any()) } throws IllegalStateException("private-credential")
            controller.submit()
            runCurrent()
            assertFalse(controller.notice.value.contains("private-credential"))
            assertTrue(controller.notice.value.contains("failed"))
            assertEquals("original", draft)
        }
}
