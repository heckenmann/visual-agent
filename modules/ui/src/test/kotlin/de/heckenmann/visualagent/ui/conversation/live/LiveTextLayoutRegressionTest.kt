package de.heckenmann.visualagent.ui.conversation.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.IdlingResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveTextLayoutRegressionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `shorter replacement retains its target layout when the animation settles`() {
        var text by mutableStateOf("Transfer processing complete")
        val waiting = AtomicBoolean(false)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.registerIdlingResource(
            object : IdlingResource {
                override val isIdleNow: Boolean get() = !waiting.get()
            },
        )
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(180.dp)) {
                    LiveAnimatedText(text, "execution", MaterialTheme.typography.bodyMedium, maxLines = 2, onTextLayout = {
                        layouts += it
                        if (it.layoutInput.placeholders.isNotEmpty()) waiting.set(false)
                    })
                }
            }
        }
        compose.runOnIdle {
            layouts.clear()
            waiting.set(true)
            text = "Transfer done complete"
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val animated = layouts.first { it.layoutInput.placeholders.isNotEmpty() }
            val settled = layouts.last()
            assertTrue(settled.layoutInput.placeholders.isNotEmpty())
            assertEquals(animated.size, settled.size)
            val suffix = text.indexOf("complete")
            assertEquals(animated.getBoundingBox(suffix), settled.getBoundingBox(suffix))
        }
    }

    @Test
    fun `preview retains newest wrapped log lines rather than the first six lines of the tail entries`() {
        val logs = List(6) { "Entry $it contains several words that wrap across multiple lines" }
        compose.setContent {
            MaterialTheme {
                val measurer = rememberTextMeasurer()
                val style = MaterialTheme.typography.bodySmall
                val (preview, overflow) = liveLogTail(logs, style, measurer, 160)
                assertTrue(overflow)
                assertTrue(preview.endsWith(logs.last()))
                assertFalse(preview.contains("Entry 0"))
                val layout =
                    measurer.measure(
                        preview,
                        style,
                        constraints =
                            androidx.compose.ui.unit
                                .Constraints(maxWidth = 160),
                    )
                assertTrue(layout.lineCount <= 6)
                assertEquals("First\nSecond", liveLogTail(listOf("First", "Second"), style, measurer, 160).first)
            }
        }
    }
}
