@file:Suppress("FunctionName")

package de.heckenmann.visualagent.ui.compose

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
            val scrollState = ScrollState(initial = 0)
            val totalWidth = 2000
            val viewportWidth = 800
            composeTestRule.setContent {
                MaterialTheme {
                    Box(
                        modifier =
                            Modifier
                                .width(viewportWidth.dp)
                                .horizontalScroll(scrollState),
                    ) {
                        Box(modifier = Modifier.width(totalWidth.dp)) {
                            ScrollArrow(
                                direction = 1,
                                scrollState = scrollState,
                                isClosing = { false },
                            )
                        }
                    }
                }
            }

            composeTestRule.waitUntil(timeoutMillis = 2_000) { scrollState.maxValue > 0 }
            composeTestRule.onNodeWithContentDescription("Scroll right").performClick()
            composeTestRule.waitForIdle()

            assertTrue(scrollState.value > 0)
        }

    @Test
    fun `scroll arrow does not launch scroll when application is closing`() =
        runTest {
            val lifecycle = ApplicationLifecycle()
            lifecycle.beginShutdown()
            val scrollState = ScrollState(initial = 0)

            scrollArrowHandler(
                direction = 1,
                scrollState = scrollState,
                scope = this,
                isClosing = { lifecycle.closing },
            )

            assertEquals(0, scrollState.value)
        }
}
