package de.heckenmann.visualagent.ui.workspace

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.AppIdentity
import de.heckenmann.visualagent.ui.components.ActionTooltip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

/** Displays the non-interactive application identity without shifting the header during loading. */
@Composable
internal fun ComposeWorkspaceBrand() {
    val bitmap by produceState<ImageBitmap?>(null) {
        value = loadWorkspaceBrand()
    }
    ActionTooltip(description = AppIdentity.DISPLAY_NAME) {
        Box(Modifier.size(40.dp)) {
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = AppIdentity.DISPLAY_NAME,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

/** Loads the bundled identity asset without reading or decoding it on the UI thread. */
private suspend fun loadWorkspaceBrand(): ImageBitmap? =
    try {
        val bytes = withContext(Dispatchers.IO) { AppIdentity.iconUrl()?.openStream()?.use { it.readBytes() } }
        bytes?.let { withContext(Dispatchers.Default) { SkiaImage.makeFromEncoded(it).toComposeImageBitmap() } }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
