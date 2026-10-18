package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies theme-owned icon animation without changing composer layout using the controlled Compose clock. */
class PromptInterpolationMagicTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `light theme animates only the wand without reserving status space`() = verifyAnimation(lightColorScheme())

    @Test
    fun `dark theme animates only the wand without reserving status space`() = verifyAnimation(darkColorScheme())

    private fun verifyAnimation(colors: ColorScheme) {
        compose.mainClock.autoAdvance = false
        val active = mutableStateOf(false)
        compose.setContent {
            MaterialTheme(colorScheme = colors) {
                Box(Modifier.width(360.dp).background(colors.surface)) {
                    ComposerActions("draft", false, active.value, false, {}, {}, {}, interpolating = active.value)
                }
            }
        }
        val indicator = compose.onNodeWithTag("prompt-interpolation-action")
        val bounds = indicator.getBoundsInRoot()
        assertEquals(40.dp, bounds.right - bounds.left)
        assertEquals(40.dp, bounds.bottom - bounds.top)
        compose.runOnIdle { active.value = true }
        compose.mainClock.advanceTimeByFrame()
        indicator.assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
        indicator.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Interpolating prompt"))
        val before = indicator.captureToImage().toPixelMap()
        val halo = colors.secondary.copy(alpha = .12f * .38f).compositeOver(colors.surface)
        assertTrue(before.containsColor(halo), "The halo must use the active theme's accent")

        compose.mainClock.advanceTimeBy(700)

        val after = indicator.captureToImage().toPixelMap()
        assertEquals(bounds, indicator.getBoundsInRoot())
        assertTrue(before.differsFrom(after), "The wand must gently sway and breathe")
        compose.runOnIdle { active.value = false }
        compose.mainClock.advanceTimeByFrame()
        val stopped = indicator.captureToImage().toPixelMap()
        compose.mainClock.advanceTimeBy(700)
        assertEquals(bounds, indicator.getBoundsInRoot())
        assertFalse(stopped.differsFrom(indicator.captureToImage().toPixelMap()), "The idle wand must not animate")
        indicator.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ProgressBarRangeInfo))
    }

    private fun PixelMap.differsFrom(other: PixelMap): Boolean {
        assertEquals(width, other.width)
        assertEquals(height, other.height)
        return (0 until width).any { x -> (0 until height).any { y -> this[x, y] != other[x, y] } }
    }

    private fun PixelMap.containsColor(expected: Color): Boolean =
        (0 until width).any { x ->
            (0 until height).any { y ->
                val actual = this[x, y]
                abs(actual.red - expected.red) < .005f &&
                    abs(actual.green - expected.green) < .005f &&
                    abs(actual.blue - expected.blue) < .005f
            }
        }
}
