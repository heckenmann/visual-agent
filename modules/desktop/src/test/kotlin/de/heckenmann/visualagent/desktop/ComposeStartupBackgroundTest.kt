package de.heckenmann.visualagent.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ThemeMode
import de.heckenmann.visualagent.ui.workspace.visualAgentDarkColorScheme
import de.heckenmann.visualagent.ui.workspace.visualAgentLightColorScheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Covers bundled image decoding, theme selection, responsive coverage, and safe fallback. */
class ComposeStartupBackgroundTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `translucent onboarding uses theme foreground in both modes`() {
        val dark = mutableStateOf(true)
        var foreground = Color.Unspecified
        compose.setContent {
            MaterialTheme(colorScheme = if (dark.value) visualAgentDarkColorScheme() else visualAgentLightColorScheme()) {
                ComposeOnboardingBackground(dark.value) {
                    foreground = LocalContentColor.current
                    Text("Readable onboarding")
                }
            }
        }
        compose.runOnIdle { assertEquals(visualAgentDarkColorScheme().onSurface, foreground) }
        compose.runOnIdle { dark.value = false }
        compose.runOnIdle { assertEquals(visualAgentLightColorScheme().onSurface, foreground) }
        compose.onNodeWithText("Readable onboarding").assertIsDisplayed()
    }

    @Test
    fun `theme change requests the matching variant and keeps the image decorative`() {
        val bitmap = runBlocking { assertNotNull(loadStartupBackground(false)) }
        val dark = mutableStateOf(false)
        val requests = CopyOnWriteArrayList<Boolean>()
        compose.setContent {
            MaterialTheme {
                ComposeStartupBackground(dark.value, Modifier.fillMaxSize(), load = {
                    requests.add(it)
                    bitmap
                }) {
                    Text("Startup status")
                }
            }
        }
        compose.waitUntil(10_000) { requests.contains(false) }
        compose.runOnIdle { dark.value = true }
        compose.waitUntil(10_000) { requests.contains(true) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("startup-background-image").fetchSemanticsNodes().isNotEmpty() }
        assertFalse(
            compose
                .onNodeWithTag("startup-background-image")
                .fetchSemanticsNode()
                .config
                .contains(SemanticsProperties.ContentDescription),
        )
        compose.onNodeWithText("Startup status").assertIsDisplayed()
    }

    @Test
    fun `bundled variants have identical HiDPI dimensions`() =
        runBlocking {
            val light = assertNotNull(loadStartupBackground(false))
            val dark = assertNotNull(loadStartupBackground(true))
            assertEquals(1519, light.width)
            assertEquals(1035, light.height)
            assertEquals(light.width, dark.width)
            assertEquals(light.height, dark.height)
        }

    @Test
    fun `missing and malformed resources fall back but cancellation propagates`(): Unit =
        runBlocking {
            assertNull(loadStartupBackground(false) { throw IllegalStateException("Missing resource") })
            assertNull(loadStartupBackground(true) { byteArrayOf(0, 1, 2) })
            assertFailsWith<CancellationException> {
                loadStartupBackground(true) { throw CancellationException("Window closed") }
            }
        }

    @Test
    fun `explicit theme choices override system appearance`() {
        var light = true
        var dark = false
        compose.setContent {
            light = startupDarkTheme(ThemeMode.LIGHT)
            dark = startupDarkTheme(ThemeMode.DARK)
        }
        compose.runOnIdle {
            assertFalse(light)
            assertTrue(dark)
        }
    }

    @Test
    fun `background covers resized bounds without changing foreground sizing`() {
        val bitmap = runBlocking { assertNotNull(loadStartupBackground(true)) }
        val size = mutableStateOf(DpSize(360.dp, 240.dp))
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(size.value)) {
                    ComposeStartupBackground(true, Modifier.fillMaxSize(), load = { bitmap }) {
                        Text("Accessible status")
                    }
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("startup-background-image").fetchSemanticsNodes().isNotEmpty() }
        for (bounds in listOf(DpSize(360.dp, 240.dp), DpSize(240.dp, 360.dp), DpSize(540.dp, 180.dp))) {
            compose.runOnIdle { size.value = bounds }
            compose.waitForIdle()
            assertEquals(
                compose.onNodeWithTag("startup-background").fetchSemanticsNode().boundsInRoot,
                compose.onNodeWithTag("startup-background-image").fetchSemanticsNode().boundsInRoot,
            )
            compose.onNodeWithText("Accessible status").assertIsDisplayed()
        }
    }

    @Test
    fun `fallback still displays foreground without an image`() {
        compose.setContent {
            MaterialTheme {
                ComposeStartupBackground(false, Modifier.fillMaxSize(), load = { null }) {
                    Text("Startup continues")
                }
            }
        }
        compose.onNodeWithText("Startup continues").assertIsDisplayed()
        compose.onNodeWithTag("startup-background-image").assertDoesNotExist()
    }
}
