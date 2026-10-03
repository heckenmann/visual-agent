package de.heckenmann.visualagent.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import de.heckenmann.visualagent.desktop.generated.resources.Res
import de.heckenmann.visualagent.protocol.ThemeMode
import de.heckenmann.visualagent.ui.workspace.isSystemInDarkTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Resolves startup appearance without blocking OS queries on the UI thread. */
@Composable
internal fun startupDarkTheme(mode: ThemeMode): Boolean = isSystemInDarkTheme(mode)

/** Shared decorative, full-bleed layer for splash and onboarding, with safe resource fallback. */
@Composable
internal fun ComposeStartupBackground(
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    load: suspend (Boolean) -> ImageBitmap? = ::loadStartupBackground,
    content: @Composable BoxScope.() -> Unit,
) {
    val bitmap by produceState<ImageBitmap?>(null, darkTheme, load) {
        value = null
        value = load(darkTheme)
    }
    Box(modifier.background(MaterialTheme.colorScheme.background).testTag("startup-background")) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().testTag("startup-background-image"),
            )
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.28f)))
        }
        content()
    }
}

/** Reads and decodes only the selected bundled image off the presentation thread. */
internal suspend fun loadStartupBackground(
    darkTheme: Boolean,
    read: suspend (String) -> ByteArray = { Res.readBytes(it) },
): ImageBitmap? =
    try {
        val bytes = withContext(Dispatchers.IO) { read(startupBackgroundPath(darkTheme)) }
        withContext(Dispatchers.Default) { bytes.decodeToImageBitmap() }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

/** Centralized packaged resource paths shared by both startup screens. */
internal fun startupBackgroundPath(darkTheme: Boolean): String =
    if (darkTheme) "files/startup_background_dark.png" else "files/startup_background_light.png"

/** Keeps onboarding foreground colors readable even when its surface is translucent. */
@Composable
internal fun ComposeOnboardingBackground(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    ComposeStartupBackground(darkTheme, Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.50f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            content()
        }
    }
}
