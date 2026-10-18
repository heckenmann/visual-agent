package de.heckenmann.visualagent.ui.conversation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import de.heckenmann.visualagent.protocol.ConversationInputPlacement
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises both actual composer placements and the rendered Magic animation. */
class PromptInterpolationComposerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `message composer animates and can cancel interpolation`() = exercise(false)

    @Test
    fun `fixed composer animates and can cancel interpolation`() = exercise(true)

    private fun exercise(fixed: Boolean) {
        val active = mutableStateOf(false)
        var requests = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                if (fixed) {
                    ConversationInputOverlay(
                        input = "draft",
                        sending = false,
                        isRequestActive = false,
                        contextReduced = false,
                        interpolationBusy = active.value,
                        interpolating = active.value,
                        onInterpolate = {
                            requests++
                            active.value = true
                        },
                        onInputChange = {},
                        onSend = {},
                        onCancel = { active.value = false },
                        onClear = {},
                        inputPlacement = ConversationInputPlacement.FIXED,
                        onInputPlacementChange = {},
                        inputFocusRequester = FocusRequester(),
                        modifier = Modifier.testTag("composer"),
                        ghostText = "",
                        ghostCursorVisible = false,
                        onFocusChanged = {},
                        onSizeChanged = {},
                    )
                } else {
                    ConversationInputCard(
                        input = "draft",
                        sending = false,
                        isRequestActive = false,
                        contextReduced = false,
                        interpolationBusy = active.value,
                        interpolating = active.value,
                        onInterpolate = {
                            requests++
                            active.value = true
                        },
                        onInputChange = {},
                        onSend = {},
                        onCancel = { active.value = false },
                        onClear = {},
                        inputPlacement = ConversationInputPlacement.CONVERSATION_MESSAGE,
                        onInputPlacementChange = {},
                        inputFocusRequester = FocusRequester(),
                        modifier = Modifier.testTag("composer"),
                    )
                }
            }
        }
        val idleBounds = compose.onNodeWithTag("composer").getBoundsInRoot()
        compose.onNodeWithContentDescription("Refine prompt").performClick()
        compose.mainClock.advanceTimeBy(240)
        assertEquals(idleBounds, compose.onNodeWithTag("composer").getBoundsInRoot())
        compose.onNodeWithContentDescription("Refine prompt").assertIsNotEnabled()
        assertEquals(1, requests)
        val before = compose.onNodeWithTag("composer").captureToImage().toPixelMap()
        compose.mainClock.advanceTimeBy(400)
        val after = compose.onNodeWithTag("composer").captureToImage().toPixelMap()
        assertTrue((0 until before.width).any { x -> (0 until before.height).any { y -> before[x, y] != after[x, y] } })
        compose.onNodeWithContentDescription("Cancel prompt interpolation").performClick()
        compose.mainClock.advanceTimeBy(240)
        assertEquals(idleBounds, compose.onNodeWithTag("composer").getBoundsInRoot())
        compose.onNodeWithContentDescription("Send message").assertIsDisplayed()
    }
}
