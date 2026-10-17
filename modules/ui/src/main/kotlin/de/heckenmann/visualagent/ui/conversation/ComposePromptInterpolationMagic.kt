package de.heckenmann.visualagent.ui.conversation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** Animated wand and sparkles, composed only while interpolation runs. */
@Composable
internal fun PromptInterpolationMagic() {
    val transition = rememberInfiniteTransition(label = "prompt-interpolation-magic")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1_600, easing = LinearEasing)), label = "magic-sparkles")
    val accent = MaterialTheme.colorScheme.tertiary
    val secondary = MaterialTheme.colorScheme.primary
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(28.dp)) {
            drawLine(secondary, Offset(size.width * .2f, size.height * .8f), Offset(size.width * .64f, size.height * .36f), 3.dp.toPx())
            repeat(4) { index ->
                val cycle = (phase + index * .25f) % 1f
                val brightness = (.3f + .7f * sin(cycle * PI).toFloat()).coerceIn(0f, 1f)
                val center = Offset(size.width * (.35f + index * .17f), size.height * (.12f + (index % 2) * .28f))
                val radius = (2f + 2f * brightness).dp.toPx()
                val color = accent.copy(alpha = brightness)
                drawLine(color, center - Offset(radius, 0f), center + Offset(radius, 0f), strokeWidth = 1.5.dp.toPx())
                drawLine(color, center - Offset(0f, radius), center + Offset(0f, radius), strokeWidth = 1.5.dp.toPx())
            }
        }
        Text("Interpolating prompt…", color = accent, style = MaterialTheme.typography.labelMedium)
    }
}
