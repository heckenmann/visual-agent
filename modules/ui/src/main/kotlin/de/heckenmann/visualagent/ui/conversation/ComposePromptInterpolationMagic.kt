package de.heckenmann.visualagent.ui.conversation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Animates only the wand's drawing layer; interpolation never adds a status row or changes layout. */
@Composable
internal fun promptInterpolationIconModifier(active: Boolean): Modifier {
    if (!active) return Modifier
    val transition = rememberInfiniteTransition(label = "prompt-interpolation-wand")
    val phase = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2_400, easing = LinearEasing)), label = "wand-breath")
    val accent = MaterialTheme.colorScheme.secondary
    return Modifier
        .graphicsLayer {
            val wave = sin(phase.value * 2 * PI).toFloat()
            rotationZ = wave * 9f
            scaleX = 1f + wave * .06f
            scaleY = scaleX
        }.drawBehind {
            val breath = ((1 - cos(phase.value * 2 * PI)) / 2).toFloat()
            drawCircle(accent.copy(alpha = .12f + breath * .24f), radius = size.minDimension * .5f)
        }
}
