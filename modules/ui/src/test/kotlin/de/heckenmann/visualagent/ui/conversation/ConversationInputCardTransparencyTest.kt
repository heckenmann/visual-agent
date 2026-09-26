package de.heckenmann.visualagent.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.protocol.ConversationInputPlacement
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertTrue

/** Verifies that the shared composer surface remains translucent in either placement. */
class ConversationInputCardTransparencyTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `composer surface reflects its backdrop`() {
        val darkBackdropPixel = renderComposerOver(Color.Black)
        val lightBackdropPixel = renderComposerOver(Color.White)
        val channelDifference =
            abs(darkBackdropPixel.red - lightBackdropPixel.red) +
                abs(darkBackdropPixel.green - lightBackdropPixel.green) +
                abs(darkBackdropPixel.blue - lightBackdropPixel.blue)

        assertTrue(channelDifference > 0.5f, "Composer surface should remain translucent")
    }

    private fun renderComposerOver(backdrop: Color): Color {
        composeTestRule.setContent {
            MaterialTheme {
                Box(Modifier.size(360.dp, 180.dp).background(backdrop).testTag("conversation-backdrop")) {
                    ConversationInputCard(
                        input = "",
                        sending = false,
                        onInputChange = {},
                        onSend = {},
                        onCancel = {},
                        onClear = {},
                        inputPlacement = ConversationInputPlacement.CONVERSATION_MESSAGE,
                        onInputPlacementChange = {},
                        inputFocusRequester = FocusRequester(),
                        modifier = Modifier.fillMaxWidth().testTag("composer-card"),
                    )
                }
            }
        }

        val bounds = composeTestRule.onNodeWithTag("composer-card").getBoundsInRoot()
        val pixels = composeTestRule.onNodeWithTag("conversation-backdrop").captureToImage().toPixelMap()
        val x = with(composeTestRule.density) { (bounds.left + (bounds.right - bounds.left) / 2).roundToPx() }
        val y = with(composeTestRule.density) { (bounds.top + 16.dp).roundToPx() }
        return pixels[x, y]
    }
}
