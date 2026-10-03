package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Verifies resize positioning without immediate or repeated list remeasurement. */
class ConversationResizeScrollEffectTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `real reverse list preserves latest and browsed positions while resizing`() {
        val list = LazyListState()
        val height = mutableStateOf(400.dp)
        val width = mutableStateOf(400.dp)
        val viewport = mutableStateOf(IntSize.Zero)
        var scope: CoroutineScope? = null
        compose.setContent {
            scope = rememberCoroutineScope()
            val following by remember { derivedStateOf { list.conversationPosition().isAtLatest } }
            ConversationResizeScrollEffect(viewport.value, true, list, following)
            LazyColumn(
                state = list,
                reverseLayout = true,
                modifier = Modifier.width(width.value).height(height.value).onSizeChanged { viewport.value = it },
            ) {
                items(40, key = { it }) { index -> Text("Message $index: " + "Variable width content. ".repeat(12)) }
            }
        }
        compose.waitForIdle()
        repeat(5) { index ->
            compose.runOnIdle {
                width.value = (300 + index * 40).dp
                height.value = (250 + index * 30).dp
            }
            compose.waitForIdle()
            assertEquals(0, list.firstVisibleItemIndex)
            assertEquals(0, list.firstVisibleItemScrollOffset)
        }
        compose.runOnIdle { checkNotNull(scope).launch { list.scrollToItem(10) } }
        compose.waitForIdle()
        val position = list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset
        compose.runOnIdle { height.value = 500.dp }
        compose.waitForIdle()
        assertEquals(position, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)
    }

    @Test
    fun `resize requests next layout once and never forces immediate scrolling`() {
        val list = mockk<LazyListState>()
        val info = mockk<LazyListLayoutInfo>()
        every { info.totalItemsCount } returns 1
        every { list.layoutInfo } returns info
        every { list.requestScrollToItem(any(), any()) } returns Unit
        coEvery { list.scrollToItem(any(), any()) } returns Unit
        val viewport = mutableStateOf(IntSize(800, 600))
        val following = mutableStateOf(true)
        compose.setContent { ConversationResizeScrollEffect(viewport.value, true, list, following.value) }
        compose.waitForIdle()
        repeat(5) { index ->
            compose.runOnIdle { viewport.value = IntSize(800 + index, 500 + index) }
            compose.waitForIdle()
        }
        verify(exactly = 6) { list.requestScrollToItem(0, 0) }
        coVerify(exactly = 0) { list.scrollToItem(any(), any()) }

        compose.runOnIdle {
            following.value = false
            viewport.value = IntSize(1000, 700)
        }
        compose.waitForIdle()
        verify(exactly = 6) { list.requestScrollToItem(0, 0) }
        coVerify(exactly = 0) { list.scrollToItem(any(), any()) }
    }
}
