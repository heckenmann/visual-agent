@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests for the workspace scroll arrow and its lifecycle guard.
 */
class ScrollArrowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `application lifecycle starts not closing`() {
        val lifecycle = ApplicationLifecycle()
        assertFalse(lifecycle.closing)
    }

    @Test
    fun `application lifecycle marks closing after beginShutdown`() {
        val lifecycle = ApplicationLifecycle()
        lifecycle.beginShutdown()
        assertTrue(lifecycle.closing)
    }

    @Test
    fun `scroll arrow advances scroll state when clicked`(): Unit =
        runTest {
            val scrollState = LazyListState(firstVisibleItemIndex = 0)
            val viewportWidth = 800
            val itemWidth = 400
            composeTestRule.setContent {
                MaterialTheme {
                    Box(modifier = Modifier.width(viewportWidth.dp)) {
                        LazyRow(
                            state = scrollState,
                            modifier = Modifier.width(viewportWidth.dp),
                        ) {
                            item {
                                ScrollArrow(
                                    direction = 1,
                                    scrollState = scrollState,
                                    isClosing = { false },
                                )
                            }
                            items(5) {
                                Box(modifier = Modifier.width(itemWidth.dp))
                            }
                        }
                    }
                }
            }

            composeTestRule.waitUntil(timeoutMillis = 2_000) { scrollState.maxScrollOffset() > 0 }
            composeTestRule.onNodeWithContentDescription("Scroll right").performClick()
            composeTestRule.waitForIdle()

            assertTrue(scrollState.scrollOffset() > 0)
        }

    @Test
    fun `scroll arrow does not launch scroll when application is closing`() =
        runTest {
            val lifecycle = ApplicationLifecycle()
            lifecycle.beginShutdown()
            val scrollState = LazyListState(firstVisibleItemIndex = 0)

            scrollArrowHandler(
                direction = 1,
                scrollState = scrollState,
                scope = this,
                isClosing = { lifecycle.closing },
            )

            assertEquals(0, scrollState.scrollOffset())
        }

    @Test
    fun `continuous scroll repeats until edge is reached`(): Unit =
        runTest {
            val scrollState = LazyListState(firstVisibleItemIndex = 0)
            val viewportWidth = 800
            val itemWidth = 400
            var composeScope: CoroutineScope? = null
            composeTestRule.setContent {
                composeScope = rememberCoroutineScope()
                MaterialTheme {
                    Box(modifier = Modifier.width(viewportWidth.dp)) {
                        LazyRow(
                            state = scrollState,
                            modifier = Modifier.width(viewportWidth.dp),
                        ) {
                            item {
                                ScrollArrow(
                                    direction = 1,
                                    scrollState = scrollState,
                                    isClosing = { false },
                                )
                            }
                            items(5) {
                                Box(modifier = Modifier.width(itemWidth.dp))
                            }
                        }
                    }
                }
            }

            composeTestRule.waitUntil(timeoutMillis = 2_000) { scrollState.maxScrollOffset() > 0 }
            val job =
                startContinuousScroll(
                    direction = 1,
                    scrollState = scrollState,
                    scope = composeScope ?: this,
                    isClosing = { false },
                )
            composeTestRule.waitUntil(timeoutMillis = 10_000) { scrollState.reachedMaxScroll() }
            job.cancel()

            assertTrue(scrollState.reachedMaxScroll())
        }

    @Test
    fun `continuous scroll stops at min edge for left direction`(): Unit =
        runTest {
            val scrollState = LazyListState(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 500)
            val viewportWidth = 800
            val itemWidth = 400
            var composeScope: CoroutineScope? = null
            composeTestRule.setContent {
                composeScope = rememberCoroutineScope()
                MaterialTheme {
                    Box(modifier = Modifier.width(viewportWidth.dp)) {
                        LazyRow(
                            state = scrollState,
                            modifier = Modifier.width(viewportWidth.dp),
                        ) {
                            item {
                                ScrollArrow(
                                    direction = -1,
                                    scrollState = scrollState,
                                    isClosing = { false },
                                )
                            }
                            items(5) {
                                Box(modifier = Modifier.width(itemWidth.dp))
                            }
                        }
                    }
                }
            }

            composeTestRule.waitUntil(timeoutMillis = 2_000) { scrollState.maxScrollOffset() > 0 }
            val job =
                startContinuousScroll(
                    direction = -1,
                    scrollState = scrollState,
                    scope = composeScope ?: this,
                    isClosing = { false },
                )
            composeTestRule.waitUntil(timeoutMillis = 5_000) { scrollState.scrollOffset() <= 0 }
            job.cancel()

            assertTrue(scrollState.scrollOffset() <= 0)
        }

    @Test
    fun `continuous scroll returns when application is closing`(): Unit =
        runTest {
            val lifecycle = ApplicationLifecycle()
            lifecycle.beginShutdown()
            val scrollState = LazyListState(firstVisibleItemIndex = 0)
            val job = startContinuousScroll(direction = 1, scrollState = scrollState, scope = this, isClosing = { lifecycle.closing })
            job.join()

            assertEquals(0, scrollState.scrollOffset())
        }

    private fun LazyListState.scrollOffset(): Int = firstVisibleItemIndex * 1_000_000 + firstVisibleItemScrollOffset

    private fun LazyListState.maxScrollOffset(): Int =
        (
            layoutInfo
                .visibleItemsInfo
                .lastOrNull()
                ?.offset
                ?.plus(layoutInfo.visibleItemsInfo.lastOrNull()?.size ?: 0) ?: 0
        ) -
            layoutInfo.viewportSize.width

    private fun LazyListState.reachedMaxScroll(): Boolean = scrollOffset() >= maxScrollOffset()
}
