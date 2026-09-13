package de.heckenmann.visualagent.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.heckenmann.visualagent.AppIdentity
import de.heckenmann.visualagent.desktop.generated.resources.Res
import de.heckenmann.visualagent.desktop.generated.resources.visual_agent
import de.heckenmann.visualagent.ui.application.StartupPhase
import de.heckenmann.visualagent.ui.application.StartupStatus
import org.jetbrains.compose.resources.painterResource

private val SplashContentShape = RoundedCornerShape(28.dp)
private val SplashStatusShape = RoundedCornerShape(18.dp)
private val SplashIconShape = RoundedCornerShape(32.dp)

/** Renders the polished server-selection experience inside the desktop splash window. */
@Composable
internal fun ComposeStartupSplashPresentation(
    status: StartupStatus,
    bookmarks: DesktopServerBookmarkLoadResult,
    onStartLocal: () -> Unit,
    onRetry: () -> Unit,
    onCreateServer: () -> Unit,
    onEditServer: (DesktopServerBookmark) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(colors.surface, colors.surfaceContainerHigh, colors.surface),
                    ),
                ),
    ) {
        SplashAmbientLight(
            modifier = Modifier.align(Alignment.TopStart).offset(x = 40.dp, y = (-104).dp),
            color = colors.primary,
        )
        SplashAmbientLight(
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 120.dp, y = 152.dp),
            color = colors.tertiary,
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(44.dp),
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SplashHero(modifier = Modifier.weight(0.88f).fillMaxHeight())
            Card(
                modifier = Modifier.weight(1.12f).fillMaxHeight(),
                shape = SplashContentShape,
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer.copy(alpha = 0.92f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    ComposeStartupServerBookmarks(
                        loadResult = bookmarks,
                        onStartLocal = onStartLocal,
                        onCreateServer = onCreateServer,
                        onEditServer = onEditServer,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    SplashStatus(status = status, onRetry = onRetry)
                }
            }
        }
    }
}

@Composable
private fun SplashAmbientLight(
    modifier: Modifier,
    color: Color,
) {
    Box(
        modifier =
            modifier
                .size(280.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent))),
    )
}

@Composable
private fun SplashHero(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(92.dp),
            shape = SplashIconShape,
            color = colors.primaryContainer.copy(alpha = 0.9f),
            shadowElevation = 16.dp,
        ) {
            Image(
                painter = painterResource(Res.drawable.visual_agent),
                contentDescription = AppIdentity.DISPLAY_NAME,
                modifier = Modifier.padding(18.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = "Visual Agent",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "A thoughtful workspace for capable agents.",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = colors.tertiary)
            Spacer(Modifier.width(10.dp))
            Text(
                "Choose a Visual Agent server to begin.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SplashStatus(
    status: StartupStatus,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().border(1.dp, colors.outlineVariant, SplashStatusShape),
        shape = SplashStatusShape,
        color = colors.surfaceContainerHigh.copy(alpha = 0.78f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (status.phase != StartupPhase.WAITING_FOR_SERVER_SELECTION && status.phase != StartupPhase.FAILED) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = status.message(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = if (status.phase == StartupPhase.FAILED) colors.error else colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (status.phase == StartupPhase.FAILED) {
                Button(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Retry")
                }
            }
        }
    }
}
