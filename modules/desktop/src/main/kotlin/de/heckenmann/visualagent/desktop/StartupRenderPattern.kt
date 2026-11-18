package de.heckenmann.visualagent.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs

private const val COLUMNS = 4
private const val ROWS = 3
private const val MAX_FILE_BYTES = 8 * 1024 * 1024
private const val MAX_CROP_EDGE = 1024
private const val CHANNEL_TOLERANCE = 32

// Distinct adjacent colors and unequal row sequences make translations and flips detectable.
private val pattern =
    intArrayOf(
        0xFFD83242.toInt(),
        0xFF1C8655.toInt(),
        0xFF2459D0.toInt(),
        0xFFF2CC39.toInt(),
        0xFF820FD5.toInt(),
        0xFFF2CC39.toInt(),
        0xFF24C4D0.toInt(),
        0xFF1C8655.toInt(),
        0xFF2459D0.toInt(),
        0xFFD83242.toInt(),
        0xFF1C8655.toInt(),
        0xFF820FD5.toInt(),
    )

/** Paints the opt-in marker in the same native window as the startup content. */
@Composable
internal fun StartupRenderPattern(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize(), propagateMinConstraints = true) { content() }
        Canvas(Modifier.align(AbsoluteAlignment.BottomLeft).padding(12.dp).size(96.dp, 72.dp)) {
            val cell = Size(size.width / COLUMNS, size.height / ROWS)
            pattern.forEachIndexed { index, color ->
                drawRect(
                    color = Color(color),
                    topLeft = Offset(index % COLUMNS * cell.width, index / COLUMNS * cell.height),
                    size = cell,
                )
            }
        }
    }
}

/** Outcome of checking a user-supplied crop of the marker area. */
internal enum class StartupPatternResult(
    val status: String,
    val exitCode: Int,
) {
    MATCH("pattern-match", 0),
    MISMATCH("pattern-mismatch", 1),
    UNKNOWN("unknown", 2),
}

/** Opens only the explicitly supplied image. An unreadable or unsuitable crop yields unknown. */
internal fun inspectStartupPattern(cropPath: String): StartupPatternResult =
    try {
        val bytes = Files.newInputStream(Path.of(cropPath)).use { it.readNBytes(MAX_FILE_BYTES + 1) }
        if (bytes.size > MAX_FILE_BYTES) StartupPatternResult.UNKNOWN else inspectStartupPattern(bytes)
    } catch (_: IOException) {
        StartupPatternResult.UNKNOWN
    } catch (_: IllegalArgumentException) {
        StartupPatternResult.UNKNOWN
    }

/** Compares interior samples at scale-independent positions without capturing the screen. */
internal fun inspectStartupPattern(bytes: ByteArray): StartupPatternResult =
    try {
        Image.makeFromEncoded(bytes).use { image ->
            val width = image.width
            val height = image.height
            if (width !in 32..MAX_CROP_EDGE ||
                height !in 24..MAX_CROP_EDGE ||
                abs(width.toDouble() / height - COLUMNS.toDouble() / ROWS) > 0.12
            ) {
                StartupPatternResult.UNKNOWN
            } else {
                Bitmap.makeFromImage(image).use { bitmap ->
                    if (pattern.indices.all { index -> matchesRegion(bitmap, index) }) {
                        StartupPatternResult.MATCH
                    } else {
                        StartupPatternResult.MISMATCH
                    }
                }
            }
        }
    } catch (_: RuntimeException) {
        StartupPatternResult.UNKNOWN
    }

private fun matchesRegion(
    bitmap: Bitmap,
    index: Int,
): Boolean {
    val expected = pattern[index]
    var matches = 0
    for (y in 1..3) {
        for (x in 1..3) {
            val pixelX = (index % COLUMNS * 4 + x) * bitmap.width / (COLUMNS * 4)
            val pixelY = (index / COLUMNS * 4 + y) * bitmap.height / (ROWS * 4)
            val color = bitmap.getColor(pixelX, pixelY)
            if (withinColorTolerance(color, expected)) matches++
        }
    }
    return matches >= 8
}

private fun withinColorTolerance(
    actual: Int,
    expected: Int,
): Boolean =
    (0..3).all { channel ->
        val shift = channel * 8
        abs((actual ushr shift and 0xFF) - (expected ushr shift and 0xFF)) <= CHANNEL_TOLERANCE
    }
