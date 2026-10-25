package de.heckenmann.visualagent.ui.conversation

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.IdlingResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.heckenmann.visualagent.model.ToolLiveState
import de.heckenmann.visualagent.protocol.ActivityPort
import de.heckenmann.visualagent.protocol.ConversationPort
import de.heckenmann.visualagent.protocol.ConversationSuggestionPort
import de.heckenmann.visualagent.protocol.ToolActivity
import de.heckenmann.visualagent.protocol.ToolActivityPhase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class ConversationLiveSnapshotEffectTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `blocked snapshot retrieval leaves composition responsive and does not reload history`() {
        val activity = mockk<ActivityPort>(relaxed = true)
        val history = mockk<ConversationPort>()
        val suggestions = mockk<ConversationSuggestionPort>(relaxed = true)
        val requests = Channel<Thread>(Channel.UNLIMITED)
        val replies = Channel<List<ToolActivity>>(Channel.UNLIMITED)
        val rendered = Channel<List<ToolLiveState>>(Channel.UNLIMITED)
        val applyingSnapshot = AtomicBoolean(false)
        val listener = slot<(ToolActivity) -> Unit>()
        every { activity.addToolListener(capture(listener)) } returns AutoCloseable {}
        compose.registerIdlingResource(
            object : IdlingResource {
                override val isIdleNow: Boolean
                    get() = !applyingSnapshot.get()
            },
        )
        every { activity.activeToolActivities() } answers {
            runBlocking {
                requests.send(Thread.currentThread())
                replies.receive()
            }
        }
        val state = ConversationUiState(emptyList())
        compose.setContent {
            ConversationActivityHistoryEffect(activity, history, state, suggestions)
            Text(state.input)
            val snapshots =
                state.liveTools.snapshots.values
                    .toList()
            LaunchedEffect(snapshots) {
                rendered.send(snapshots)
                applyingSnapshot.set(false)
            }
        }
        runBlocking { rendered.receive() }
        val reader = runBlocking { requests.receive() }
        compose.runOnIdle {
            assertNotSame(Thread.currentThread(), reader)
            state.input = "Still responsive"
        }
        compose.onNodeWithText("Still responsive").assertExists()
        val snapshot = ToolLiveState("execution", 1, "Waiting")
        val newer = snapshot.copy(revision = 2, status = "Paused")
        compose.runOnIdle {
            listener.captured(ToolActivity("sleep", phase = ToolActivityPhase.UPDATED, executionId = "execution", liveState = newer))
        }
        compose.waitForIdle()
        applyingSnapshot.set(true)
        runBlocking {
            replies.send(listOf(ToolActivity("sleep", phase = ToolActivityPhase.STARTED, executionId = "execution", liveState = snapshot)))
        }
        compose.waitForIdle()
        assertEquals(listOf(newer), runBlocking { rendered.receive() })
        compose.runOnIdle { state.input = "Recomposed" }
        compose.waitForIdle()
        verify(exactly = 1) { activity.activeToolActivities() }
        coVerify(exactly = 0) { history.latest() }
    }
}
