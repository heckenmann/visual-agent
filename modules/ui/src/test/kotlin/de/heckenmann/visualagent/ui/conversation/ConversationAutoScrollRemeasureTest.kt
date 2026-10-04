package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ConversationMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies next-measure auto-follow without cancellable frame-based scroll corrections. */
class ConversationAutoScrollRemeasureTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `content changes request remeasurement without suspended scrolling`() {
        val list = mockk<LazyListState>()
        val info = mockk<LazyListLayoutInfo>()
        every { info.totalItemsCount } returns 40
        every { list.layoutInfo } returns info
        every { list.requestScrollToItem(any(), any()) } returns Unit
        coEvery { list.scrollToItem(any(), any()) } coAnswers { awaitCancellation() }
        val pending = mutableStateOf<String?>(null)
        compose.setContent {
            ConversationScrollOnChangeEffect(
                timeline = pending.value?.let { listOf(ConversationTimelineItem.PendingUser(it, "pending")) }.orEmpty(),
                listState = list,
            )
        }
        compose.waitForIdle()
        compose.runOnIdle { pending.value = "New message" }
        compose.waitForIdle()
        verify(exactly = 1) { list.requestScrollToItem(0, 0) }

        coVerify(exactly = 0) { list.scrollToItem(any(), any()) }
        compose.runOnIdle { pending.value = "Another message while browsing" }
        compose.waitForIdle()
        verify(exactly = 2) { list.requestScrollToItem(0, 0) }
        coVerify(exactly = 0) { list.scrollToItem(any(), any()) }
    }

    @Test
    fun `stable keyed newest messages and stream growth keep reverse list at latest`() {
        val list = LazyListState()
        val history = mutableStateOf((1..40).map { message(it) })
        val stream = mutableStateOf("")
        compose.setContent {
            ConversationScrollOnChangeEffect(
                timeline = timeline(history.value, stream.value),
                listState = list,
            )
            LazyColumn(state = list, reverseLayout = true, modifier = Modifier.width(200.dp).height(160.dp)) {
                if (stream.value.isNotEmpty()) {
                    item(key = "stream") { Text(stream.value) }
                }
                items(history.value.asReversed(), key = { checkNotNull(it.id) }) { Text(it.content) }
            }
        }
        compose.waitForIdle()
        repeat(6) { index ->
            compose.runOnIdle { history.value = history.value + message(41 + index) }
            compose.waitForIdle()
            assertTrue(list.conversationPosition().isAtLatest)
        }
        repeat(6) { index ->
            compose.runOnIdle { stream.value += "Chunk $index\n".repeat(8) }
            compose.waitForIdle()
            assertTrue(list.conversationPosition().isAtLatest)
        }
    }

    @Test
    fun `new messages and chunks jump from browsing while older pages preserve the anchor`() {
        val list = LazyListState()
        val history = mutableStateOf((21..60).map { message(it) })
        val stream = mutableStateOf("Initial response")
        var scope: CoroutineScope? = null
        compose.setContent {
            scope = rememberCoroutineScope()
            ConversationScrollOnChangeEffect(
                timeline = timeline(history.value, stream.value),
                listState = list,
            )
            LazyColumn(state = list, reverseLayout = true, modifier = Modifier.width(200.dp).height(160.dp)) {
                item(key = "stream") { Text(stream.value) }
                items(history.value.asReversed(), key = { checkNotNull(it.id) }) { Text(it.content) }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { checkNotNull(scope).launch { list.scrollToItem(15, 4) } }
        compose.waitForIdle()
        compose.runOnIdle { history.value = history.value + message(61) }
        compose.waitForIdle()
        assertTrue(list.conversationPosition().isAtLatest)
        compose.runOnIdle { checkNotNull(scope).launch { list.scrollToItem(15, 4) } }
        compose.waitForIdle()
        compose.runOnIdle { stream.value += "\nAdditional chunk".repeat(20) }
        compose.waitForIdle()
        assertTrue(list.conversationPosition().isAtLatest)
        compose.runOnIdle { checkNotNull(scope).launch { list.scrollToItem(15, 4) } }
        compose.waitForIdle()
        val anchor =
            list.layoutInfo.visibleItemsInfo
                .first()
                .key
        val offset = list.firstVisibleItemScrollOffset
        compose.runOnIdle { history.value = (1..20).map { message(it) } + history.value }
        compose.waitForIdle()
        assertEquals(
            anchor,
            list.layoutInfo.visibleItemsInfo
                .first()
                .key,
        )
        assertEquals(offset, list.firstVisibleItemScrollOffset)
    }

    private fun message(index: Int) = ConversationMessage("assistant", "Message $index\n".repeat(5), id = "message-$index")

    private fun timeline(
        history: List<ConversationMessage>,
        stream: String,
    ) = buildConversationTimeline(
        history = history,
        pendingUserMessage = null,
        streamingContent = stream,
        streamingEntryId = "stream",
        requestActive = false,
        showOlderHistoryLoading = false,
        includeInlineComposer = false,
    )
}
